package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDate;
import java.util.List;

/** Reads customer orders from the old ERP. Read-only by contract. */
public interface LegacyCustomerOrderReader {

    /** Orders dated on or after {@code since}, oldest first, at most {@code limit} rows. */
    List<LegacyCustomerOrderRow> fetchDatedSince(LocalDate since, int limit);
}
