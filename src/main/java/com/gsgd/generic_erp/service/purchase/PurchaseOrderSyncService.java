package com.gsgd.generic_erp.service.purchase;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.LegacySyncState;
import com.gsgd.generic_erp.entity.purchase.LegacyUserMap;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.LegacySyncStateRepository;
import com.gsgd.generic_erp.repository.purchase.LegacyUserMapRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.PlannedCheckpoint;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderRow;

import lombok.RequiredArgsConstructor;

/**
 * Copies purchase orders from the old ERP into purchase_order_tb and creates
 * their follow-up checkpoints.
 * <ol>
 * <li>Reads rows changed since the last run (legacy_sync_state_tb).</li>
 * <li>A new active order gets the default rule and its checkpoints. Windows
 * that already ended before today are not created: an order first seen on day
 * 10 starts follow-up at its current window instead of showing missed checks
 * nobody could have done.</li>
 * <li>An existing order is updated. When it becomes received, closed or
 * cancelled, its open checkpoints are cancelled.</li>
 * </ol>
 * One run is one transaction: if it fails, nothing is half-written and the
 * next run reads the same rows again.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderSyncService {

    public static final String SYNC_KEY = "PURCHASE_ORDER";
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSyncService.class);

    private final ObjectProvider<LegacyPurchaseOrderReader> readerProvider;
    private final ObjectProvider<LegacyPurchaseOrderLineReader> lineReaderProvider;
    private final FollowUpTrackService trackService;
    private final LegacyErpProperties props;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FollowUpCheckpointRepository checkpointRepository;
    private final LegacySyncStateRepository syncStateRepository;
    private final LegacyUserMapRepository userMapRepository;
    private final UserRepository userRepository;
    private final FollowUpRuleService ruleService;
    private final Clock clock;

    /** What one run did. */
    public record SyncResult(String status, int rowsRead, int created, int updated, int closed, String message) {
        static SyncResult disabled() {
            return new SyncResult("DISABLED", 0, 0, 0, 0, "erp.legacy.enabled is false");
        }
    }

    @Transactional
    public SyncResult syncNow() {
        LegacyPurchaseOrderReader reader = readerProvider.getIfAvailable();
        if (reader == null) {
            return SyncResult.disabled();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LegacySyncState state = syncStateRepository.findById(SYNC_KEY)
                .orElseGet(() -> LegacySyncState.builder().syncKey(SYNC_KEY).build());
        LocalDateTime since = state.getLastSourceUpdatedAt() != null
                ? state.getLastSourceUpdatedAt()
                : now.minusDays(props.getPurchaseOrder().getInitialLookbackDays());

        List<LegacyPurchaseOrderRow> rows = reader.fetchChangedSince(since,
                props.getPurchaseOrder().getBatchLimit());

        int created = 0;
        int updated = 0;
        int closed = 0;
        LocalDateTime maxSeen = state.getLastSourceUpdatedAt();
        List<String> skipped = new ArrayList<>();
        LegacyPurchaseOrderLineReader lineReader = lineReaderProvider.getIfAvailable();
        boolean withLines = lineReader != null;
        Map<String, PurchaseOrder> touched = new LinkedHashMap<>();
        Set<Long> needSplit = new HashSet<>();
        for (LegacyPurchaseOrderRow row : rows) {
            if (row.poNo() == null || row.poNo().isBlank() || row.orderDate() == null) {
                skipped.add(String.valueOf(row.poNo()));
                continue;
            }
            Outcome outcome = apply(row, now, withLines);
            if (withLines) {
                purchaseOrderRepository.findBySourceSystemAndExternalPoNo(props.getSourceSystem(), row.poNo())
                        .ifPresent(po -> {
                            touched.put(po.getExternalPoNo(), po);
                            if (outcome == Outcome.CREATED || outcome == Outcome.REOPENED) {
                                needSplit.add(po.getId());
                            }
                        });
            }
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED, REOPENED -> updated++;
                case CLOSED -> closed++;
            }
            if (row.updatedAt() != null && (maxSeen == null || row.updatedAt().isAfter(maxSeen))) {
                maxSeen = row.updatedAt();
            }
        }

        if (withLines && !touched.isEmpty()) {
            splitByPlanType(lineReader, touched, needSplit, now);
        }

        String message = skipped.isEmpty() ? null
                : I18n.t("purchase.sync.skippedRows", String.join(", ", skipped));
        state.setLastSourceUpdatedAt(maxSeen);
        state.setLastRunAt(now);
        state.setLastStatus("OK");
        state.setLastRowCount(rows.size());
        state.setLastMessage(message == null ? null : truncate(message, 1000));
        syncStateRepository.save(state);
        return new SyncResult("OK", rows.size(), created, updated, closed, message);
    }

    /** Records a failed run without touching the high-water mark. */
    @Transactional
    public void recordFailure(Exception e) {
        LegacySyncState state = syncStateRepository.findById(SYNC_KEY)
                .orElseGet(() -> LegacySyncState.builder().syncKey(SYNC_KEY).build());
        state.setLastRunAt(LocalDateTime.now(clock));
        state.setLastStatus("FAILED");
        state.setLastMessage(truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 1000));
        syncStateRepository.save(state);
    }

    public Optional<LegacySyncState> lastState() {
        return syncStateRepository.findById(SYNC_KEY);
    }

    /**
     * Reads the lines of the orders this run touched, stores them, and splits
     * the follow-up of every active order that is new, reopened or whose lines
     * changed into one task series per separate plan group.
     */
    private void splitByPlanType(LegacyPurchaseOrderLineReader lineReader, Map<String, PurchaseOrder> touched,
            Set<Long> needSplit, LocalDateTime now) {
        List<LegacyPurchaseOrderLineRow> lines = lineReader.fetchLines(touched.keySet());
        List<PurchaseOrder> changed = trackService.storeLines(touched.values(), lines, now);
        changed.forEach(po -> needSplit.add(po.getId()));
        List<PurchaseOrder> refresh = touched.values().stream()
                .filter(po -> needSplit.contains(po.getId()) && po.getStatus().isActive()).toList();
        trackService.refresh(refresh, now);
    }

    enum Outcome {
        CREATED, UPDATED, REOPENED, CLOSED
    }

    Outcome apply(LegacyPurchaseOrderRow row, LocalDateTime now) {
        return apply(row, now, false);
    }

    /**
     * @param withLines true when lines are synced too: the follow-up tasks of
     *                  a new or reopened order are then planned per plan group
     *                  after its lines are read, not here
     */
    Outcome apply(LegacyPurchaseOrderRow row, LocalDateTime now, boolean withLines) {
        PurchaseOrderStatus status = mapStatus(row.status());
        Optional<PurchaseOrder> existing = purchaseOrderRepository
                .findBySourceSystemAndExternalPoNo(props.getSourceSystem(), row.poNo());

        if (existing.isEmpty()) {
            PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                    .sourceSystem(props.getSourceSystem())
                    .externalPoNo(row.poNo())
                    .supplierCode(row.supplierCode())
                    .supplierName(row.supplierName())
                    .buyerCode(row.buyerCode())
                    .purchaserId(resolvePurchaser(row.buyerCode()))
                    .status(status)
                    .sourceStatus(row.status())
                    .orderDate(row.orderDate())
                    .expectedDeliveryDate(row.expectedDate())
                    .followupRuleId(ruleService.defaultRuleId())
                    .sourceUpdatedAt(row.updatedAt())
                    .syncedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            if (status.isActive() && !withLines) {
                createCheckpoints(po, now);
            }
            return Outcome.CREATED;
        }

        PurchaseOrder po = existing.get();
        boolean wasActive = po.getStatus().isActive();
        po.setSupplierCode(row.supplierCode());
        po.setSupplierName(row.supplierName());
        if (!java.util.Objects.equals(po.getBuyerCode(), row.buyerCode()) || po.getPurchaserId() == null) {
            po.setBuyerCode(row.buyerCode());
            po.setPurchaserId(resolvePurchaser(row.buyerCode()));
        }
        po.setStatus(status);
        po.setSourceStatus(row.status());
        po.setOrderDate(row.orderDate());
        po.setExpectedDeliveryDate(row.expectedDate());
        po.setSourceUpdatedAt(row.updatedAt());
        po.setSyncedAt(now);
        po.setUpdatedAt(now);
        purchaseOrderRepository.save(po);

        if (wasActive && !status.isActive()) {
            checkpointRepository.changeStatus(po.getId(), List.of(CheckpointStatus.OPEN), CheckpointStatus.CANCELLED);
            return Outcome.CLOSED;
        }
        if (!wasActive && status.isActive()) {
            if (withLines) {
                return Outcome.REOPENED;
            }
            if (!checkpointRepository.existsByPurchaseOrderId(po.getId())) {
                createCheckpoints(po, now);
            }
        }
        return Outcome.UPDATED;
    }

    private void createCheckpoints(PurchaseOrder po, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<PlannedCheckpoint> plan = ruleService.plan(po.getFollowupRuleId(), po.getOrderDate());
        List<FollowUpCheckpoint> rows = plan.stream()
                .filter(p -> !p.windowEnd().isBefore(today))
                .map(p -> FollowUpCheckpoint.builder()
                        .purchaseOrderId(po.getId())
                        .stage(p.stage())
                        .seq(p.seq())
                        .windowStart(p.windowStart())
                        .windowEnd(p.windowEnd())
                        .status(CheckpointStatus.OPEN)
                        .createdAt(now)
                        .build())
                .toList();
        checkpointRepository.saveAll(rows);
        if (rows.size() < plan.size()) {
            log.info("PO {}: {} of {} follow-up windows had already ended when first synced",
                    po.getExternalPoNo(), plan.size() - rows.size(), plan.size());
        }
    }

    PurchaseOrderStatus mapStatus(String raw) {
        if (raw == null) {
            return PurchaseOrderStatus.PLACED;
        }
        String key = raw.strip();
        PurchaseOrderStatus mapped = props.getPurchaseOrder().getStatusMap().get(key);
        if (mapped != null) {
            return mapped;
        }
        // The default old-ERP query already returns this system's status names.
        try {
            return PurchaseOrderStatus.valueOf(key);
        } catch (IllegalArgumentException e) {
            return PurchaseOrderStatus.PLACED;
        }
    }

    /** Mapping table first, then a user whose username equals the buyer code. */
    Long resolvePurchaser(String buyerCode) {
        if (buyerCode == null || buyerCode.isBlank()) {
            return null;
        }
        return userMapRepository.findBySourceSystemAndLegacyCode(props.getSourceSystem(), buyerCode)
                .map(LegacyUserMap::getUserId)
                .or(() -> userRepository.findByUsername(buyerCode).map(User::getId))
                .orElse(null);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
