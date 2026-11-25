package com.gsgd.generic_erp.service.purchase.legacy;

import java.time.LocalDate;
import java.util.List;

import com.gsgd.generic_erp.enums.PlacementOrderType;

/**
 * Reads the documents that decide purchase placement tasks from the old ERP:
 * purchase applications (采购申请) for sample orders and purchase plans
 * (采购计划) for production orders. Read-only by contract.
 */
public interface LegacyPlacementSourceReader {

    /**
     * Document lines dated on or after {@code since}, at most {@code limit}
     * rows. Returns an empty list when no query is configured for the type.
     */
    List<LegacyPlacementSourceRow> fetch(PlacementOrderType type, LocalDate since, int limit);

    /** True when a query is configured for the type. */
    boolean supports(PlacementOrderType type);
}
