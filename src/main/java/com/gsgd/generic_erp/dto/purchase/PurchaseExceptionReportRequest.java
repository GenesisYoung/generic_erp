package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDate;

/**
 * Body of POST /api/purchase-exceptions. Give either {@code purchaseOrderId}
 * or {@code poNo}. {@code category}: DELAY, SHORTAGE, QUALITY, PRICE,
 * CAPACITY, OTHER; {@code severity}: NORMAL, HIGH, URGENT (default NORMAL).
 */
public record PurchaseExceptionReportRequest(Long purchaseOrderId, String poNo, String category, String severity,
        String description, LocalDate impactDate) {
}
