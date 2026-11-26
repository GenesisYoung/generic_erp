package com.gsgd.generic_erp.service.purchase.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Reads what a purchase quotation (报价方案) needs from the old ERP: orders
 * whose purchase plan exists but nothing is bought yet, their order BOMs and
 * the supplier prices of the purchase strategy (采购策略). Read-only by
 * contract.
 */
public interface LegacyQuotationReader {

    /** True when all three queries are configured. */
    boolean supported();

    /** Order lines of plans dated on or after {@code since}, at most {@code limit}. */
    List<OrderLineRow> orderLines(LocalDate since, int limit);

    /**
     * BOM lines of one customer order. {@code lineIds} are the ERIs of its
     * order lines (ORDCORD2.ERI) as {@link #orderLines} read them; the query
     * selects by them, so it finds exactly the lines the order list showed.
     */
    List<BomLineRow> bomLines(String orderNo, Collection<String> lineIds);

    /**
     * One log line that says which server and database the application reads
     * and how many rows each step of the BOM lookup finds. Only called when
     * {@link #bomLines} returned nothing.
     */
    default String diagnose(String orderNo, Collection<String> lineIds) {
        return "";
    }

    /** Valid supplier prices of the given materials (any order). */
    List<PriceRow> prices(Collection<String> materialCodes);

    /**
     * One customer order line that can be quoted. {@code planLines} and
     * {@code planLinesBought} count the lines of its purchase plan and how many
     * of them already have a purchase order line (null when not known).
     */
    record OrderLineRow(String orderNo, String customerCode, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, String planNo, LocalDate planDate, String lineId, String productCode,
            String productName, String spec, BigDecimal qty, String unit, String productBomCode,
            String packagingBomCode, Integer planLines, Integer planLinesBought) {
    }

    /**
     * One BOM line. {@code bomKind} is PRODUCT, PACKAGING or null (then the
     * material class decides). {@code qtyPerUnit} is the quantity per ONE
     * parent ({@code parentKey}); the calculator multiplies it up the tree
     * ({@code lineKey} / {@code parentKey}) to get the quantity per finished
     * product. {@code orderQty} is the order line quantity.
     */
    record BomLineRow(String bomKind, String bomCode, String lineId, String productCode, BigDecimal orderQty,
            Integer lineNo, Integer level, String materialCode, String materialName, String spec, String unit,
            String materialClass, BigDecimal qtyPerUnit, boolean leaf, BigDecimal basePrice, String lineKey,
            String parentKey) {
    }

    /** One supplier's price for one material. */
    record PriceRow(String materialCode, String supplierCode, String supplierName, BigDecimal price,
            BigDecimal taxRate, String currency, LocalDate validFrom, LocalDate validTo) {
    }
}
