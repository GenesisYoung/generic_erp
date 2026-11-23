package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder;

/**
 * One line of the purchaser's to-do list: a checkpoint whose window is open now.
 *
 * @param daysLeft days from today to the window's last day; 0 means "due today"
 */
public record FollowUpTodoItem(
        Long checkpointId,
        FollowUpStage stage,
        int seq,
        LocalDate windowStart,
        LocalDate windowEnd,
        long daysLeft,
        Long purchaseOrderId,
        String poNo,
        String supplierCode,
        String supplierName,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        Long templateId,
        String templateName) {

    public static FollowUpTodoItem of(CheckpointAndOrder row, LocalDate today, String templateName) {
        var c = row.checkpoint();
        var po = row.order();
        return new FollowUpTodoItem(c.getId(), c.getStage(), c.getSeq(), c.getWindowStart(), c.getWindowEnd(),
                ChronoUnit.DAYS.between(today, c.getWindowEnd()), po.getId(), po.getExternalPoNo(),
                po.getSupplierCode(), po.getSupplierName(), po.getOrderDate(), po.getExpectedDeliveryDate(),
                po.getFollowupRuleId(), templateName);
    }
}
