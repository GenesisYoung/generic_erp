package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.FollowUpPlanGroup;
import com.gsgd.generic_erp.entity.purchase.FollowUpPlanType;
import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.entity.purchase.FollowUpTrack;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrderLine;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanGroupRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanTypeRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpTrackRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderLineRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.PlannedCheckpoint;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineRow;

import lombok.RequiredArgsConstructor;

/**
 * Splits a purchase order's follow-up into task series by purchase plan type.
 * <p>
 * Every line has a plan type ({@link PurchaseOrderLine#getPlanTypeCode()}).
 * A plan type belongs to at most one plan group ({@link FollowUpPlanType});
 * types in the same group count as the same plan. For an order:
 * <ul>
 * <li>each <b>separate</b> group that still has open lines in the order gets
 * its own task series ({@link FollowUpTrack}), planned from the group's
 * template or the default one;</li>
 * <li>every other open line (no type, type not grouped, or group not
 * separate) stays in the <b>main</b> series, planned from the order's
 * template. When no such line is left, the main series ends.</li>
 * <li>An order without synced lines has only the main series, as before plan
 * groups existed.</li>
 * </ul>
 * Ending a series deletes its OPEN tasks that have not started and cancels
 * the OPEN ones that have; DONE and MISSED tasks stay as history.
 */
@Service
@RequiredArgsConstructor
public class FollowUpTrackService {

    static final List<PurchaseOrderStatus> ACTIVE = List.of(PurchaseOrderStatus.PLACED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final FollowUpPlanTypeRepository typeRepository;
    private final FollowUpPlanGroupRepository groupRepository;
    private final FollowUpTrackRepository trackRepository;
    private final FollowUpCheckpointRepository checkpointRepository;
    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpRuleService ruleService;
    private final Clock clock;

    /** What a refresh changed: task series started and ended. */
    public record RefreshResult(int orders, int seriesStarted, int seriesEnded) {
        RefreshResult plus(RefreshResult o) {
            return new RefreshResult(orders + o.orders, seriesStarted + o.seriesStarted, seriesEnded + o.seriesEnded);
        }
    }

    // ----- lines from the sync ------------------------------------------------

    /**
     * Replaces the stored lines of the given orders with the rows read from
     * the old ERP (an order with no rows loses its lines), and registers plan
     * types not seen before.
     *
     * @return the orders whose lines changed, so only they need a refresh
     */
    @Transactional
    public List<PurchaseOrder> storeLines(Collection<PurchaseOrder> orders, List<LegacyPurchaseOrderLineRow> rows,
            LocalDateTime now) {
        Map<String, List<LegacyPurchaseOrderLineRow>> byNo = rows.stream()
                .filter(r -> r.poNo() != null && r.lineNo() != null)
                .collect(Collectors.groupingBy(LegacyPurchaseOrderLineRow::poNo, LinkedHashMap::new,
                        Collectors.toList()));
        registerTypes(rows, now);

        List<Long> ids = orders.stream().map(PurchaseOrder::getId).toList();
        Map<Long, List<PurchaseOrderLine>> existing = ids.isEmpty() ? Map.of()
                : lineRepository.findByPurchaseOrderIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseOrderLine::getPurchaseOrderId));
        List<PurchaseOrder> changed = new ArrayList<>();
        for (PurchaseOrder po : orders) {
            List<PurchaseOrderLine> fresh = new ArrayList<>();
            Set<Integer> seen = new LinkedHashSet<>();
            for (LegacyPurchaseOrderLineRow r : byNo.getOrDefault(po.getExternalPoNo(), List.of())) {
                if (!seen.add(r.lineNo())) {
                    continue; // the unique key is (order, line no.); keep the first
                }
                fresh.add(PurchaseOrderLine.builder()
                        .purchaseOrderId(po.getId())
                        .lineNo(r.lineNo())
                        .materialCode(truncate(r.materialCode(), 60))
                        .materialName(truncate(r.materialName(), 255))
                        .planTypeCode(truncate(r.planType(), 50))
                        .qty(r.qty())
                        .receivedQty(r.receivedQty())
                        .syncedAt(now)
                        .build());
            }
            List<PurchaseOrderLine> old = existing.getOrDefault(po.getId(), List.of());
            if (sameLines(old, fresh)) {
                continue;
            }
            lineRepository.deleteByPurchaseOrderId(po.getId());
            lineRepository.flush();
            lineRepository.saveAll(fresh);
            changed.add(po);
        }
        return changed;
    }

    private void registerTypes(List<LegacyPurchaseOrderLineRow> rows, LocalDateTime now) {
        Map<String, String> names = new LinkedHashMap<>();
        for (LegacyPurchaseOrderLineRow r : rows) {
            if (r.planType() != null) {
                names.merge(truncate(r.planType(), 50), r.planTypeName() == null ? "" : r.planTypeName(),
                        (a, b) -> a.isEmpty() ? b : a);
            }
        }
        if (names.isEmpty()) {
            return;
        }
        Map<String, FollowUpPlanType> known = typeRepository.findByTypeCodeIn(names.keySet()).stream()
                .collect(Collectors.toMap(FollowUpPlanType::getTypeCode, t -> t));
        List<FollowUpPlanType> save = new ArrayList<>();
        names.forEach((code, name) -> {
            FollowUpPlanType t = known.get(code);
            if (t == null) {
                t = FollowUpPlanType.builder().typeCode(code).createdAt(now).build();
            }
            if ((t.getTypeName() == null || t.getTypeName().isBlank()) && !name.isBlank()) {
                t.setTypeName(truncate(name, 100));
            }
            t.setLastSeenAt(now);
            save.add(t);
        });
        typeRepository.saveAll(save);
    }

    private static boolean sameLines(List<PurchaseOrderLine> a, List<PurchaseOrderLine> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Map<Integer, PurchaseOrderLine> byNo = a.stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getLineNo, l -> l, (x, y) -> x));
        for (PurchaseOrderLine n : b) {
            PurchaseOrderLine o = byNo.get(n.getLineNo());
            if (o == null || !Objects.equals(o.getPlanTypeCode(), n.getPlanTypeCode())
                    || !Objects.equals(o.getQty(), n.getQty()) || !Objects.equals(o.getReceivedQty(), n.getReceivedQty())
                    || !Objects.equals(o.getMaterialCode(), n.getMaterialCode())
                    || !Objects.equals(o.getMaterialName(), n.getMaterialName())) {
                return false;
            }
        }
        return true;
    }

    // ----- splitting ----------------------------------------------------------

    /** Re-checks every active order, e.g. after the plan groups changed. */
    @Transactional
    public RefreshResult refreshAllOpen() {
        LocalDateTime now = LocalDateTime.now(clock);
        Rules rules = loadRules();
        RefreshResult total = new RefreshResult(0, 0, 0);
        for (PurchaseOrder po : purchaseOrderRepository.findByStatusIn(ACTIVE)) {
            total = total.plus(refresh(po, now, rules));
        }
        return total;
    }

    /** Re-checks the given orders (the sync calls this for orders that are new or whose lines changed). */
    @Transactional
    public RefreshResult refresh(Collection<PurchaseOrder> orders, LocalDateTime now) {
        Rules rules = loadRules();
        RefreshResult total = new RefreshResult(0, 0, 0);
        for (PurchaseOrder po : orders) {
            total = total.plus(refresh(po, now, rules));
        }
        return total;
    }

    /** Plan types and groups, loaded once per refresh run. */
    record Rules(Map<String, Long> separateGroupOfType, Map<Long, FollowUpPlanGroup> groups) {
    }

    Rules loadRules() {
        Map<Long, FollowUpPlanGroup> groups = groupRepository.findByActiveTrueOrderByGroupNameAsc().stream()
                .collect(Collectors.toMap(FollowUpPlanGroup::getId, g -> g));
        Map<String, Long> separate = new HashMap<>();
        for (FollowUpPlanType t : typeRepository.findAll()) {
            FollowUpPlanGroup g = t.getGroupId() == null ? null : groups.get(t.getGroupId());
            if (g != null && g.isSeparate()) {
                separate.put(t.getTypeCode(), g.getId());
            }
        }
        return new Rules(separate, groups);
    }

    RefreshResult refresh(PurchaseOrder po, LocalDateTime now, Rules rules) {
        if (!po.getStatus().isActive()) {
            return new RefreshResult(0, 0, 0);
        }
        LocalDate today = now.toLocalDate();
        List<PurchaseOrderLine> lines = lineRepository.findByPurchaseOrderIdOrderByLineNoAsc(po.getId());
        List<PurchaseOrderLine> open = lines.stream().filter(PurchaseOrderLine::isOpen).toList();
        if (open.isEmpty()) {
            open = lines; // nothing open per line but the order is still active: follow all of it
        }
        Set<Long> neededGroups = new LinkedHashSet<>();
        boolean mainNeeded = open.isEmpty();
        for (PurchaseOrderLine l : open) {
            Long g = l.getPlanTypeCode() == null ? null : rules.separateGroupOfType().get(l.getPlanTypeCode());
            if (g == null) {
                mainNeeded = true;
            } else {
                neededGroups.add(g);
            }
        }

        int started = 0;
        int ended = 0;
        Map<Long, FollowUpTrack> tracks = trackRepository.findByPurchaseOrderIdOrderByIdAsc(po.getId()).stream()
                .collect(Collectors.toMap(FollowUpTrack::getPlanGroupId, t -> t));
        for (FollowUpTrack t : tracks.values()) {
            if (t.isActive() && !neededGroups.contains(t.getPlanGroupId())) {
                endSeries(po.getId(), t.getPlanGroupId(), today);
                t.setActive(false);
                t.setUpdatedAt(now);
                trackRepository.save(t);
                ended++;
            }
        }
        for (Long groupId : neededGroups) {
            FollowUpTrack t = tracks.get(groupId);
            if (t != null && t.isActive()) {
                continue;
            }
            if (t == null) {
                t = FollowUpTrack.builder().purchaseOrderId(po.getId()).planGroupId(groupId)
                        .followupRuleId(templateOf(rules.groups().get(groupId))).createdAt(now).build();
            }
            t.setActive(true);
            t.setUpdatedAt(now);
            trackRepository.save(t);
            planSeries(po, groupId, t.getFollowupRuleId(), now, false);
            started++;
        }

        if (mainNeeded) {
            if (!po.isMainFollowupNeeded()) {
                // The main series had ended and lines came back to it.
                po.setMainFollowupActive(true);
                po.setUpdatedAt(now);
                purchaseOrderRepository.save(po);
                if (openMainCount(po.getId()) == 0) {
                    planSeries(po, null, po.getFollowupRuleId(), now, false);
                }
                started++;
            } else if (!hasAnyMainCheckpoint(po.getId())) {
                // A new order: the sync leaves its main series to this method.
                planSeries(po, null, po.getFollowupRuleId(), now, false);
            }
        } else if (po.isMainFollowupNeeded()) {
            po.setMainFollowupActive(false);
            po.setUpdatedAt(now);
            purchaseOrderRepository.save(po);
            endSeries(po.getId(), null, today);
            ended++;
        }
        return new RefreshResult(1, started, ended);
    }

    private boolean hasAnyMainCheckpoint(Long poId) {
        return checkpointRepository.findByPurchaseOrderIdOrderBySeqAsc(poId).stream()
                .anyMatch(c -> c.getPlanGroupId() == null);
    }

    private int openMainCount(Long poId) {
        return checkpointRepository.findByPurchaseOrderIdAndPlanGroupIdAndStatus(poId, null, CheckpointStatus.OPEN)
                .size();
    }

    /** The group's template when it is still active; otherwise the default template. */
    private Long templateOf(FollowUpPlanGroup g) {
        if (g != null && g.getFollowupRuleId() != null
                && ruleRepository.findById(g.getFollowupRuleId()).map(FollowUpRule::isActive).orElse(false)) {
            return g.getFollowupRuleId();
        }
        return ruleService.defaultRuleId();
    }

    /** Deletes the series' OPEN tasks that have not started; cancels the OPEN ones that have. */
    void endSeries(Long poId, Long groupId, LocalDate today) {
        List<FollowUpCheckpoint> open = checkpointRepository.findByPurchaseOrderIdAndPlanGroupIdAndStatus(poId,
                groupId, CheckpointStatus.OPEN);
        List<FollowUpCheckpoint> future = open.stream().filter(c -> c.getWindowStart().isAfter(today)).toList();
        List<FollowUpCheckpoint> current = open.stream().filter(c -> !c.getWindowStart().isAfter(today)).toList();
        checkpointRepository.deleteAll(future);
        current.forEach(c -> c.setStatus(CheckpointStatus.CANCELLED));
        checkpointRepository.saveAll(current);
    }

    /**
     * Adds the template's tasks that have not ended yet to one series of the
     * order (null group = main series).
     *
     * @param clampToToday true moves a task that started in the past to start today
     * @return the tasks added
     */
    List<FollowUpCheckpoint> planSeries(PurchaseOrder po, Long groupId, Long ruleId, LocalDateTime now,
            boolean clampToToday) {
        LocalDate today = now.toLocalDate();
        int seq = checkpointRepository.maxSeq(po.getId());
        List<FollowUpCheckpoint> rows = new ArrayList<>();
        for (PlannedCheckpoint p : ruleService.plan(ruleId, po.getOrderDate())) {
            if (p.windowEnd().isBefore(today)) {
                continue;
            }
            rows.add(FollowUpCheckpoint.builder()
                    .purchaseOrderId(po.getId())
                    .planGroupId(groupId)
                    .stage(p.stage())
                    .seq(++seq)
                    .windowStart(clampToToday && p.windowStart().isBefore(today) ? today : p.windowStart())
                    .windowEnd(p.windowEnd())
                    .status(CheckpointStatus.OPEN)
                    .createdAt(now)
                    .build());
        }
        return checkpointRepository.saveAll(rows);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
