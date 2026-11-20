package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDateTime;
import java.util.List;

import com.gsgd.generic_erp.enums.PlacementTaskStatus;

/**
 * A placement task as the 采购下单任务 page sees it.
 *
 * @param hoursLeft      whole hours until the deadline; negative when overdue
 * @param linkedPoNos    purchase orders the old ERP links to this customer order
 * @param linesWithPo    customer order lines that have at least one linked PO
 * @param lineCount      lines of the customer order; null when the order is not synced
 */
public record PlacementTaskView(
        Long id,
        String orderNo,
        String customerName,
        int auditRound,
        LocalDateTime auditVerifiedAt,
        LocalDateTime deadlineAt,
        long hoursLeft,
        PlacementTaskStatus status,
        Long assigneeId,
        String assigneeName,
        LocalDateTime assignedAt,
        LocalDateTime doneAt,
        String doneSource,
        String donePoNo,
        List<String> linkedPoNos,
        int linesWithPo,
        Integer lineCount) {
}
