package com.gsgd.generic_erp.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Numbers for the PMC dashboard (pmc_manager, pmc_clerk). All counts are
 * computed when asked; {@code generatedAt} is in the business time zone.
 */
public record PmcDashboard(
        LocalDateTime generatedAt,
        CustomerOrders customerOrders,
        Audits audits,
        Placement placement,
        Exceptions exceptions,
        List<PlacementRow> overduePlacements,
        List<ExceptionRow> urgentExceptions) {

    /**
     * @param pendingAudit open orders with no order audit yet
     * @param newToday     orders first synced today
     */
    public record CustomerOrders(long pendingAudit, long newToday, LocalDateTime lastSyncAt, String lastSyncStatus) {
    }

    /**
     * @param byStatus open audit tasks per status (VERIFIED and CANCELLED left out)
     * @param overdue  open audit tasks past their effective due time
     * @param passedLast7Days audit rounds verified with PASS in the last 7 days
     */
    public record Audits(Map<String, Long> byStatus, long overdue, long passedLast7Days) {
    }

    /**
     * @param onTimeRate done tasks of the last 30 days closed by their deadline,
     *                   0..1; null when none were done
     */
    public record Placement(long open, long overdue, long dueSoon, long unassigned, long doneLast30Days,
            Double onTimeRate) {
    }

    /** Open = REPORTED, ACKNOWLEDGED or IN_PROGRESS. */
    public record Exceptions(long open, Map<String, Long> openBySeverity, Map<String, Long> openByStatus,
            long reportedLast7Days) {
    }

    public record PlacementRow(Long id, String orderNo, LocalDateTime deadlineAt, String assigneeName) {
    }

    public record ExceptionRow(Long id, String exceptionNo, String poNo, String supplierName, String category,
            String severity, String status, LocalDateTime reportedAt) {
    }
}
