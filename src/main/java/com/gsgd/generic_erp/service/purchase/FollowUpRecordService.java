package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.FollowUpRecordRequest;
import com.gsgd.generic_erp.dto.purchase.FollowUpRecordView;
import com.gsgd.generic_erp.dto.purchase.FollowUpSeriesView;
import com.gsgd.generic_erp.dto.purchase.FollowUpTodoItem;
import com.gsgd.generic_erp.dto.purchase.PurchaseOrderFollowUps;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.FollowUpPlanGroup;
import com.gsgd.generic_erp.entity.purchase.FollowUpRecord;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrderLine;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanGroupRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanTypeRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRecordRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpTrackRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderLineRepository;

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
    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpTrackRepository trackRepository;
    private final FollowUpPlanGroupRepository groupRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final FollowUpPlanTypeRepository planTypeRepository;
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
        // A record for a plan group completes that group's task. A record with no
        // group completes the main task, or, when the order has no main task
        // open now, whichever task of the order is open today.
        Optional<FollowUpCheckpoint> open = checkpointRepository.findOpenCoveringInGroup(po.getId(),
                req.planGroupId(), today);
        if (open.isEmpty() && req.planGroupId() == null) {
            open = checkpointRepository.findOpenCovering(po.getId(), today);
        }
        Long groupId = open.map(FollowUpCheckpoint::getPlanGroupId).orElse(req.planGroupId());

        FollowUpRecord saved = recordRepository.save(FollowUpRecord.builder()
                .purchaseOrderId(po.getId())
                .planGroupId(groupId)
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

    /**
     * Marks one follow-up task done (the user picks the task, so it does not
     * depend on which window contains today). Saves the follow-up record with
     * it. The task must be OPEN and its window must have started.
     *
     * @return empty when the task or its order does not exist or is not visible
     * @throws InvalidFollowUpException 400 for a bad body, 409 when the task is not open,
     *                                  has not started or the order no longer needs follow-up
     */
    @Transactional
    public Optional<Saved> completeTask(Long userId, Long checkpointId, FollowUpRecordRequest req) {
        Optional<FollowUpCheckpoint> found = checkpointRepository.findById(checkpointId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        FollowUpCheckpoint c = found.get();
        Optional<PurchaseOrder> order = queryService.find(userId, c.getPurchaseOrderId());
        if (order.isEmpty()) {
            return Optional.empty();
        }
        PurchaseOrder po = order.get();
        validate(req);
        if (!po.getStatus().isActive()) {
            throw new InvalidFollowUpException(409,
                    I18n.t("followup.error.orderInactive", po.getExternalPoNo(),
                            I18n.ref("purchase.status." + po.getStatus().name())));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (c.getStatus() != CheckpointStatus.OPEN) {
            throw new InvalidFollowUpException(409, I18n.t("followup.error.taskNotOpen"));
        }
        if (c.getWindowStart().isAfter(now.toLocalDate())) {
            throw new InvalidFollowUpException(409, I18n.t("followup.error.taskNotStarted", c.getWindowStart().toString()));
        }
        FollowUpRecord saved = recordRepository.save(FollowUpRecord.builder()
                .purchaseOrderId(po.getId())
                .planGroupId(c.getPlanGroupId())
                .checkpointId(c.getId())
                .recordedBy(userId)
                .materialStatus(req.materialStatus())
                .supplierPromisedDate(req.supplierPromisedDate())
                .missingOrderFound(Boolean.TRUE.equals(req.missingOrderFound()))
                .note(blankToNull(req.note()))
                .followedUpAt(now)
                .build());
        c.setStatus(CheckpointStatus.DONE);
        c.setDoneRecordId(saved.getId());
        return Optional.of(new Saved(FollowUpRecordView.of(saved, nameOf(userId)), checkpointRepository.save(c)));
    }

    /** The order, its checkpoints and its records. Empty when not visible. */
    public Optional<PurchaseOrderFollowUps> followUps(Long userId, Long purchaseOrderId) {
        return queryService.find(userId, purchaseOrderId).map(po -> {
            List<FollowUpRecord> records = recordRepository.findByPurchaseOrderIdOrderByFollowedUpAtDesc(po.getId());
            Map<Long, String> names = namesOf(records.stream().map(FollowUpRecord::getRecordedBy).toList());
            List<PurchaseOrderLine> lines = lineRepository
                    .findByPurchaseOrderIdOrderByLineNoAsc(po.getId());
            return new PurchaseOrderFollowUps(po,
                    checkpointRepository.findByPurchaseOrderIdOrderBySeqAsc(po.getId()),
                    records.stream().map(r -> FollowUpRecordView.of(r, names.get(r.getRecordedBy()))).toList(),
                    series(po, lines), lines);
        });
    }

    /** The order's task series: the main one first, then each plan group's, by group name. */
    private List<FollowUpSeriesView> series(PurchaseOrder po,
            List<PurchaseOrderLine> lines) {
        var tracks = trackRepository.findByPurchaseOrderIdOrderByIdAsc(po.getId());
        Map<Long, FollowUpPlanGroup> groups = new HashMap<>();
        groupRepository.findAllById(tracks.stream().map(t -> t.getPlanGroupId()).toList())
                .forEach(g -> groups.put(g.getId(), g));
        Map<Long, String> templates = templateNames(Stream
                .concat(Stream.of(po.getFollowupRuleId()), tracks.stream().map(t -> t.getFollowupRuleId()))
                .toList());
        // Which group each line is in: the group of an active track whose plan types match.
        Map<String, Long> groupOfType = new HashMap<>();
        if (!tracks.isEmpty()) {
            planTypeRepository.findAll().forEach(t -> {
                if (t.getGroupId() != null) {
                    groupOfType.put(t.getTypeCode(), t.getGroupId());
                }
            });
        }
        Set<Long> trackGroups = tracks.stream().filter(t -> t.isActive()).map(t -> t.getPlanGroupId())
                .collect(Collectors.toSet());
        Map<Long, int[]> counts = new HashMap<>();
        for (var l : lines) {
            Long g = l.getPlanTypeCode() == null ? null : groupOfType.get(l.getPlanTypeCode());
            Long key = g != null && trackGroups.contains(g) ? g : -1L;
            int[] c = counts.computeIfAbsent(key, k -> new int[2]);
            c[0]++;
            if (l.isOpen()) {
                c[1]++;
            }
        }
        List<FollowUpSeriesView> out = new ArrayList<>();
        int[] main = counts.getOrDefault(-1L, new int[2]);
        out.add(new FollowUpSeriesView(null, null, po.getFollowupRuleId(), templates.get(po.getFollowupRuleId()),
                po.isMainFollowupNeeded(), main[0], main[1]));
        tracks.stream()
                .sorted(Comparator.comparing(t -> groups.containsKey(t.getPlanGroupId())
                        ? groups.get(t.getPlanGroupId()).getGroupName() : ""))
                .forEach(t -> {
                    int[] c = counts.getOrDefault(t.getPlanGroupId(), new int[2]);
                    var g = groups.get(t.getPlanGroupId());
                    out.add(new FollowUpSeriesView(t.getPlanGroupId(), g == null ? null : g.getGroupName(),
                            t.getFollowupRuleId(), templates.get(t.getFollowupRuleId()), t.isActive(), c[0], c[1]));
                });
        return out;
    }

    private Map<Long, String> templateNames(List<Long> ruleIds) {
        Map<Long, String> names = new HashMap<>();
        ruleRepository.findAllById(ruleIds.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(r -> names.put(r.getId(), r.getRuleName()));
        return names;
    }

    /** The caller's checkpoints whose window is open now, the ones ending first on top. */
    public List<FollowUpTodoItem> todo(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<CheckpointAndOrder> rows = checkpointRepository.findTodo(userId,
                today);
        // Template of each task's series: the order's for the main series, the track's for a plan group.
        Map<String, Long> trackRule = new HashMap<>();
        List<Long> poIds = rows.stream().filter(r -> r.checkpoint().getPlanGroupId() != null)
                .map(r -> r.order().getId()).distinct().toList();
        if (!poIds.isEmpty()) {
            trackRepository.findByPurchaseOrderIdIn(poIds)
                    .forEach(t -> trackRule.put(t.getPurchaseOrderId() + ":" + t.getPlanGroupId(), t.getFollowupRuleId()));
        }
        Map<Long, String> groupNames = new HashMap<>();
        groupRepository.findAllById(rows.stream().map(r -> r.checkpoint().getPlanGroupId()).filter(Objects::nonNull)
                .distinct().toList()).forEach(g -> groupNames.put(g.getId(), g.getGroupName()));
        Function<CheckpointAndOrder, Long> ruleOf = r -> {
            Long g = r.checkpoint().getPlanGroupId();
            return g == null ? r.order().getFollowupRuleId() : trackRule.get(r.order().getId() + ":" + g);
        };
        Map<Long, String> templates = templateNames(rows.stream().map(ruleOf).toList());
        return rows.stream()
                .map(row -> {
                    Long rule = ruleOf.apply(row);
                    Long g = row.checkpoint().getPlanGroupId();
                    return FollowUpTodoItem.of(row, today, rule, templates.get(rule), g == null ? null : groupNames.get(g));
                })
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
