package com.gsgd.generic_erp.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.entity.customer.CustomerOrder;

/**
 * A customer order as the 待评审客户订单 page sees it, with the status of its
 * latest order audit round.
 *
 * @param needsReview  open in the old ERP and no order audit created yet
 * @param auditTaskId  latest audit round's task id; null when none exists
 * @param auditStatus  that task's status (DRAFT, ACCEPTED, ..., VERIFIED); null when none
 */
public record CustomerOrderView(
        Long id,
        String orderNo,
        String customerCode,
        String customerName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        String salesCode,
        Integer lineCount,
        BigDecimal totalQty,
        boolean legacyAudited,
        boolean legacyClosed,
        LocalDateTime firstSeenAt,
        boolean needsReview,
        String auditTaskId,
        Integer auditRound,
        String auditStatus) {

    public static CustomerOrderView of(CustomerOrder c, AuditTaskBrief latest) {
        return new CustomerOrderView(c.getId(), c.getOrderNo(), c.getCustomerCode(), c.getCustomerName(),
                c.getOrderDate(), c.getDeliveryDate(), c.getSalesCode(), c.getLineCount(), c.getTotalQty(),
                c.isLegacyAudited(), c.isLegacyClosed(), c.getFirstSeenAt(),
                !c.isLegacyClosed() && latest == null,
                latest == null ? null : latest.id(),
                latest == null ? null : latest.round(),
                latest == null ? null : latest.status());
    }
}
