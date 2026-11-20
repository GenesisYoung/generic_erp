package com.gsgd.generic_erp.service.purchase.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One customer order header as read from the old ERP (ORDCORD1), already in
 * this system's types.
 *
 * @param legacyId the old ERP row id (ORDCORD1.ERI); stable, used to tell a new
 *                 order from one already seen
 */
public record LegacyCustomerOrderRow(
        String legacyId,
        String orderNo,
        String customerCode,
        String customerName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        String salesCode,
        Integer lineCount,
        BigDecimal totalQty,
        boolean legacyAudited,
        boolean closed) {
}
