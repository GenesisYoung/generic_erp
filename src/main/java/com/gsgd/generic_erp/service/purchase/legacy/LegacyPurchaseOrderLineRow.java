package com.gsgd.generic_erp.service.purchase.legacy;

/**
 * One purchase order line as read from the old ERP.
 *
 * @param planType     the value that decides which follow-up task the line belongs to; null = unknown
 * @param planTypeName a readable name for {@code planType}, if the query returns one
 */
public record LegacyPurchaseOrderLineRow(
        String poNo,
        Integer lineNo,
        String materialCode,
        String materialName,
        String planType,
        String planTypeName,
        Double qty,
        Double receivedQty) {
}
