package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDateTime;

/**
 * One row of the purchase application (sample orders) or purchase plan
 * (production orders) query: a document line that buys for an order.
 *
 * @param orderNo    the order the document buys for (YP... or P...)
 * @param sourceNo   the document number of the application / plan
 * @param sourceDate the document date; the 2-day placement clock starts here
 * @param confirmed  true when the old ERP marks it confirmed for purchase (采购确认)
 */
public record LegacyPlacementSourceRow(String orderNo, String sourceNo, LocalDateTime sourceDate,
        boolean confirmed) {
}
