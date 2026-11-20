package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One purchase order as read from the old ERP, already in this system's types. */
public record LegacyPurchaseOrderRow(
        String poNo,
        String supplierCode,
        String supplierName,
        String buyerCode,
        LocalDate orderDate,
        LocalDate expectedDate,
        String status,
        LocalDateTime updatedAt) {
}
