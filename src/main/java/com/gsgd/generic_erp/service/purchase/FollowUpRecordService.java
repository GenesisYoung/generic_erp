package com.gsgd.generic_erp.service.purchase;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.dto.purchase.FollowUpRecordRequest;
import com.gsgd.generic_erp.dto.purchase.FollowUpRecordView;
import com.gsgd.generic_erp.dto.purchase.FollowUpTodoItem;
import com.gsgd.generic_erp.dto.purchase.PurchaseOrderFollowUps;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.FollowUpRecord;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRecordRepository;

import lombok.RequiredArgsConstructor;

/**
 * Saving and reading follow-up records.
 * <p>
 * A record is stamped with the server's time in the business zone. If an OPEN
 * checkpoint window of the order contains that day, the checkpoint becomes
 * DONE and points to the record. Otherwise the record is still saved as an
 * extra follow-up (the rules say "at least", so more checks are welcome).
 */
@Service
@RequiredArgsConstructor
public class FollowUpRecordService {

    static final int NOTE_MAX = 500;

    private final PurchaseOrderQueryService queryService;
    private final FollowUpCheckpointRepository checkpointRepository;
    private final FollowUpRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** Thrown for a request the caller can fix; the message is safe to show. */
    public static class InvalidFollowUpException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public InvalidFollowUpException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    /** Result of saving a record: the record and the checkpoint it completed, if any. */
    public record Saved(FollowUpRecordView record, FollowUpCheckpoint completedCheckpoint) {
    }

    /**
     * Saves a follow-up on an order the caller can see.
     *
     * @return empty when the order does not exist or is not visible to the caller
     * @throws InvalidFollowUpException 400 for a bad body, 409 when the order no longer needs follow-up
     */
    @Transactional
    public Optional<Saved> record(Long userId, Long purchaseOrderId, FollowUpRecordRequest req) {
        Optional<PurchaseOrder> found = queryService.find(userId, purchaseOrderId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PurchaseOrder po = found.get();
        validate(req);
        if (!po.getStatus().isActive()) {
            throw new InvalidFollowUpException(409,
                    I18n.t("followup.error.orderInactive", po.getExternalPoNo(),
                            I18n.ref("purchase.status." + po.getStatus().name())));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Optional<FollowUpCheckpoint> open = checkpointRepository.findOpenCovering(po.getId(), today);

        FollowUpRecord saved = recordRepository.save(FollowUpRecord.builder()
                .purchaseOrderId(po.getId())
                .checkpointId(open.map(FollowUpCheckpoint::getId).orElse(null))
                .recordedBy(userId)
                .materialStatus(req.materialStatus())
                .supplierPromisedDate(req.supplierPromisedDate())
                .missingOrderFound(Boolean.TRUE.equals(req.missingOrderFound()))
                .note(blankToNull(req.note()))
                .followedUpAt(now)
                .build());

        FollowUpCheckpoint completed = open.map(c -> {
            c.setStatus(CheckpointStatus.DONE);
            c.setDoneRecordId(saved.getId());
            return checkpointRepository.save(c);
        }).orElse(null);

        return Optional.of(new Saved(FollowUpRecordView.of(saved, nameOf(userId)), completed));
    }

    /** The order, its checkpoints and its records. Empty when not visible. */
    public Optional<PurchaseOrderFollowUps> followUps(Long userId, Long purchaseOrderId) {
        return queryService.find(userId, purchaseOrderId).map(po -> {
            List<FollowUpRecord> records = recordRepository.findByPurchaseOrderIdOrderByFollowedUpAtDesc(po.getId());
            Map<Long, String> names = namesOf(records.stream().map(FollowUpRecord::getRecordedBy).toList());
            return new PurchaseOrderFollowUps(po,
                    checkpointRepository.findByPurchaseOrderIdOrderBySeqAsc(po.getId()),
                    records.stream().map(r -> FollowUpRecordView.of(r, names.get(r.getRecordedBy()))).toList());
        });
    }

    /** The caller's checkpoints whose window is open now, the ones ending first on top. */
    public List<FollowUpTodoItem> todo(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return checkpointRepository.findTodo(userId, today).stream()
                .map(row -> FollowUpTodoItem.of(row, today))
                .toList();
    }

    private static void validate(FollowUpRecordRequest req) {
        if (req == null || req.materialStatus() == null) {
            throw new InvalidFollowUpException(400, I18n.t("followup.error.materialStatusRequired"));
        }
        if (req.note() != null && req.note().length() > NOTE_MAX) {
            throw new InvalidFollowUpException(400, I18n.t("followup.error.noteTooLong", NOTE_MAX));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private String nameOf(Long userId) {
        return namesOf(List.of(userId)).get(userId);
    }

    private Map<Long, String> namesOf(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, FollowUpRecordService::displayName, (a, b) -> a));
    }

    static String displayName(User u) {
        return u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getUsername();
    }
}
