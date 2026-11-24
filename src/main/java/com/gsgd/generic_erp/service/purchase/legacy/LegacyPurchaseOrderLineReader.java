package com.gsgd.generic_erp.service.purchase.legacy;

import java.util.Collection;
import java.util.List;

/** Reads purchase order lines from the old ERP. Read-only by contract. */
public interface LegacyPurchaseOrderLineReader {

    /** Every line of the given orders (by order number). */
    List<LegacyPurchaseOrderLineRow> fetchLines(Collection<String> poNos);
}
