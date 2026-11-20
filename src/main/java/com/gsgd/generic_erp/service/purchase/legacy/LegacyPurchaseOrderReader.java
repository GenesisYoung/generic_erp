package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDateTime;
import java.util.List;

/** Reads purchase orders from the old ERP. Read-only by contract. */
public interface LegacyPurchaseOrderReader {

    /** Orders changed after {@code since}, oldest change first, at most {@code limit} rows. */
    List<LegacyPurchaseOrderRow> fetchChangedSince(LocalDateTime since, int limit);
}
