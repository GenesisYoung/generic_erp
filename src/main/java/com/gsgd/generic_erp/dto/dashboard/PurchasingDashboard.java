package com.gsgd.generic_erp.dto.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Numbers for the purchasing dashboard. A purchasing manager
 * ({@code data.purchaseOrder.all}) sees everyone's work and the per-purchaser
 * table; a purchaser sees only their own orders, tasks and exceptions.
 *
 * @param allPurchasers true when the numbers cover every purchaser
 */
public record PurchasingDashboard(
        LocalDateTime generatedAt,
        boolean allPurchasers,
        Placement placement,
        FollowUps followUps,
        Exceptions exceptions,
        List<PurchaserRow> byPurchaser,
        List<FollowUpRow> dueFollowUps,
        List<PmcDashboard.PlacementRow> openPlacements) {

    public record Placement(long open, long overdue, long dueSoon, long unassigned) {
    }

    /**
     * @param activeOrders    orders still PLACED or PARTIALLY_RECEIVED
     * @param dueToday        OPEN checkpoints whose window contains today
     * @param missedLast7Days checkpoints that ended MISSED in the last 7 days
     */
    public record FollowUps(long activeOrders, long dueToday, long missedLast7Days) {
    }

    public record Exceptions(long open, long urgentOpen) {
    }

    public record PurchaserRow(Long purchaserId, String name, long activeOrders, long followUpsDueToday,
            long missedLast7Days, long openPlacements, long overduePlacements, long openExceptions) {
    }

    public record FollowUpRow(Long purchaseOrderId, String poNo, String supplierName, String stage, int seq,
            LocalDate windowEnd, long daysLeft) {
    }
}
