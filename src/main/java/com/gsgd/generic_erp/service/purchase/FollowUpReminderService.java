package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * The three passes the hourly follow-up job runs, in this order:
 * <ol>
 * <li><b>Missed.</b> OPEN checkpoints whose window ended before today become
 * MISSED. Each purchaser gets one summary, and every {@code purchaser_manager}
 * gets one summary of all missed follow-ups.</li>
 * <li><b>Overdue.</b> Each follow-up series of an active order (the main one
 * and each plan group's own) that has run past the last day of its
 * rule, and has no window reaching today, gets a new OVERDUE checkpoint
 * covering today ({@code overdue_period_days} long, 1 day in the default rule;
 * none when the rule leaves it empty).</li>
 * <li><b>Due.</b> OPEN checkpoints whose window contains today and that were
 * never reminded: each purchaser gets one summary, then {@code reminded_at}
 * is set.</li>
 * </ol>
 * Every pass only touches rows still in the state it looks for, so running the
 * job twice in an hour sends nothing new. Notifications go out only after the
 * transaction commits (see {@code NotificationDispatcher}).
 */
@Service
@RequiredArgsConstructor
public class FollowUpReminderService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpReminderService.class);

    static final String MANAGER_ROLE = "purchaser_manager";
    static final String BIZ_TYPE = "PURCHASE_ORDER";
    /** How many order numbers a summary lists before "and N more". */
    static final int LIST_LIMIT = 10;
    private static final List<PurchaseOrderStatus> ACTIVE = List.of(PurchaseOrderStatus.PLACED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final FollowUpCheckpointRepository checkpointRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpRuleService ruleService;
    private final com.gsgd.generic_erp.repository.purchase.FollowUpTrackRepository trackRepository;
    private final com.gsgd.generic_erp.repository.purchase.FollowUpPlanGroupRepository groupRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record RunResult(int missed, int overdueCreated, int reminded) {
    }

    @Transactional
    public RunResult runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int missed = markMissed(today);
        int overdue = createOverdueWindows(today, now);
        int reminded = remindDue(today, now);
        return new RunResult(missed, overdue, reminded);
    }

    int markMissed(LocalDate today) {
        List<CheckpointAndOrder> rows = checkpointRepository.findOpenEndedBefore(today);
        if (rows.isEmpty()) {
            return 0;
        }
        rows.forEach(r -> r.checkpoint().setStatus(CheckpointStatus.MISSED));
        Map<Long, String> groups = groupNames(rows);
        checkpointRepository.saveAll(rows.stream().map(CheckpointAndOrder::checkpoint).toList());

        byPurchaser(rows).forEach((purchaserId, list) -> events.publishEvent(NotificationRequestedEvent.localized(
                NotificationType.FOLLOWUP_MISSED, NotifactionPriority.HIGH,
                LocalizedText.of("followup.notify.missedTitle", list.size()),
                LocalizedText.of("followup.notify.missedContent", summary(list, groups)),
                BIZ_TYPE, singleOrderId(list), Set.of(purchaserId))));

        Set<Long> managers = recipientResolver.usersWithRoles(List.of(MANAGER_ROLE));
        if (!managers.isEmpty()) {
            events.publishEvent(NotificationRequestedEvent.localized(
                    NotificationType.FOLLOWUP_MISSED, NotifactionPriority.HIGH,
                    LocalizedText.of("followup.notify.missedTitle", rows.size()),
                    LocalizedText.of("followup.notify.missedManagerContent", summary(rows, groups),
                            unassignedNote(rows)),
                    BIZ_TYPE, singleOrderId(rows), managers));
        }
        return rows.size();
    }

    int createOverdueWindows(LocalDate today, LocalDateTime now) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findWithoutWindowReaching(ACTIVE, today);
        Map<Long, Integer> periodByRule = new LinkedHashMap<>();
        List<FollowUpCheckpoint> created = new ArrayList<>();
        for (PurchaseOrder po : orders) {
            int lastDay = ruleService.lastDay(po.getFollowupRuleId());
            long day = ChronoUnit.DAYS.between(po.getOrderDate(), today);
            if (day <= lastDay) {
                continue; // still inside the planned stages
            }
            int period = periodByRule.computeIfAbsent(po.getFollowupRuleId(), this::overduePeriodDays);
            if (period == 0) {
                continue; // the rule asks for no follow-up after its last stage
            }
            created.add(FollowUpCheckpoint.builder()
                    .purchaseOrderId(po.getId())
                    .stage(FollowUpStage.OVERDUE)
                    .seq(checkpointRepository.maxSeq(po.getId()) + 1)
                    .windowStart(today)
                    .windowEnd(today.plusDays(period - 1L))
                    .status(CheckpointStatus.OPEN)
                    .createdAt(now)
                    .build());
        }
        // Separate plan-group series: same rule, with the series' own template.
        Map<Long, PurchaseOrder> orderById = new LinkedHashMap<>();
        for (var t : trackRepository.findWithoutWindowReaching(ACTIVE, today)) {
            PurchaseOrder po = orderById.computeIfAbsent(t.getPurchaseOrderId(),
                    id -> purchaseOrderRepository.findById(id).orElse(null));
            if (po == null) {
                continue;
            }
            int lastDay = ruleService.lastDay(t.getFollowupRuleId());
            if (ChronoUnit.DAYS.between(po.getOrderDate(), today) <= lastDay) {
                continue;
            }
            int period = periodByRule.computeIfAbsent(t.getFollowupRuleId(), this::overduePeriodDays);
            if (period == 0) {
                continue;
            }
            created.add(FollowUpCheckpoint.builder()
                    .purchaseOrderId(po.getId())
                    .planGroupId(t.getPlanGroupId())
                    .stage(FollowUpStage.OVERDUE)
                    .seq(checkpointRepository.maxSeq(po.getId()) + 1 + (int) created.stream()
                            .filter(c -> c.getPurchaseOrderId().equals(po.getId())).count())
                    .windowStart(today)
                    .windowEnd(today.plusDays(period - 1L))
                    .status(CheckpointStatus.OPEN)
                    .createdAt(now)
                    .build());
        }
        checkpointRepository.saveAll(created);
        return created.size();
    }

    int remindDue(LocalDate today, LocalDateTime now) {
        List<CheckpointAndOrder> rows = checkpointRepository.findDueNotReminded(today);
        if (rows.isEmpty()) {
            return 0;
        }
        rows.forEach(r -> r.checkpoint().setRemindedAt(now));
        Map<Long, String> groups = groupNames(rows);
        checkpointRepository.saveAll(rows.stream().map(CheckpointAndOrder::checkpoint).toList());

        Map<Long, List<CheckpointAndOrder>> byPurchaser = byPurchaser(rows);
        byPurchaser.forEach((purchaserId, list) -> events.publishEvent(NotificationRequestedEvent.localized(
                NotificationType.FOLLOWUP_DUE, NotifactionPriority.NORMAL,
                LocalizedText.of("followup.notify.dueTitle", list.size()),
                LocalizedText.of("followup.notify.dueContent", summary(list, groups)),
                BIZ_TYPE, singleOrderId(list), Set.of(purchaserId))));

        long unassigned = rows.stream().filter(r -> r.order().getPurchaserId() == null).count();
        if (unassigned > 0) {
            log.warn("{} due follow-ups belong to orders with no purchaser; map their buyer codes in legacy_user_map_tb",
                    unassigned);
        }
        return rows.size();
    }

    /**
     * Length of each OVERDUE window for a rule. 0 when the rule has
     * {@code overdue_period_days} empty (no follow-up after its last stage).
     * An order with no rule, or a rule that no longer exists, gets the built-in
     * default of 1 (daily).
     */
    int overduePeriodDays(Long ruleId) {
        if (ruleId == null) {
            return 1;
        }
        return ruleRepository.findById(ruleId)
                .map(rule -> rule.getOverduePeriodDays() == null ? 0 : Math.max(rule.getOverduePeriodDays(), 1))
                .orElse(1);
    }

    /** Rows grouped by purchaser in query order; orders with no purchaser are left out. */
    private static Map<Long, List<CheckpointAndOrder>> byPurchaser(List<CheckpointAndOrder> rows) {
        return rows.stream()
                .filter(r -> r.order().getPurchaserId() != null)
                .collect(Collectors.groupingBy(r -> r.order().getPurchaserId(), LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * "PO1 (by 2026-11-21), PO2 (by 2026-11-22) and 3 more", as keys so each
     * reader sees it in their own language.
     */
    static LocalizedText summary(List<CheckpointAndOrder> rows) {
        return summary(rows, Map.of());
    }

    /** Same, with the plan group's name after the order number of a plan group's own task. */
    static LocalizedText summary(List<CheckpointAndOrder> rows, Map<Long, String> groupNames) {
        LocalizedText shown = LocalizedText.join(rows.stream().limit(LIST_LIMIT)
                .map(r -> {
                    Long g = r.checkpoint().getPlanGroupId();
                    String group = g == null ? null : groupNames.get(g);
                    return group == null
                            ? LocalizedText.of("followup.notify.item", r.order().getExternalPoNo(),
                                    String.valueOf(r.checkpoint().getWindowEnd()))
                            : LocalizedText.of("followup.notify.itemGroup", r.order().getExternalPoNo(), group,
                                    String.valueOf(r.checkpoint().getWindowEnd()));
                })
                .toList());
        int more = rows.size() - LIST_LIMIT;
        return more > 0 ? LocalizedText.of("followup.notify.more", shown, more) : shown;
    }

    private Map<Long, String> groupNames(List<CheckpointAndOrder> rows) {
        List<Long> ids = rows.stream().map(r -> r.checkpoint().getPlanGroupId()).filter(java.util.Objects::nonNull)
                .distinct().toList();
        Map<Long, String> names = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            groupRepository.findAllById(ids).forEach(g -> names.put(g.getId(), g.getGroupName()));
        }
        return names;
    }

    private static Object unassignedNote(List<CheckpointAndOrder> rows) {
        long n = rows.stream().filter(r -> r.order().getPurchaserId() == null).count();
        return n == 0 ? "" : LocalizedText.of("followup.notify.unassignedNote", n);
    }

    /** The order id when every row is the same order, so a click can open it; otherwise null. */
    private static Long singleOrderId(List<CheckpointAndOrder> rows) {
        Set<Long> ids = rows.stream().map(r -> r.order().getId()).collect(Collectors.toSet());
        return ids.size() == 1 ? ids.iterator().next() : null;
    }
}
