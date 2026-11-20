package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDate;

/**
 * One purchase order that buys for one customer order line, found through
 * the old ERP chain PO line (ORDSPHS2.PLANERI) -> production plan line
 * (PLANORDER2.FROMORDERI) -> customer order line (ORDCORD2.MASTERI) ->
 * customer order (ORDCORD1).
 */
public record LegacyPoLinkRow(
        String customerOrderId,
        String customerOrderNo,
        String customerLineId,
        String poNo,
        LocalDate poDate) {
}
