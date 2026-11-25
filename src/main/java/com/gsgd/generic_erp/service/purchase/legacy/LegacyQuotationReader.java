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

    /** BOM lines of one customer order. */
    List<BomLineRow> bomLines(String orderNo);

    /** Valid supplier prices of the given materials (any order). */
    List<PriceRow> prices(Collection<String> materialCodes);

    /** One customer order line that can be quoted. */
    record OrderLineRow(String orderNo, String customerCode, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, String planNo, LocalDate planDate, String lineId, String productCode,
            String productName, String spec, BigDecimal qty, String unit, String productBomCode,
            String packagingBomCode) {
    }

    /**
     * One BOM line. {@code bomKind} is PRODUCT, PACKAGING or null (then the
     * material class decides). {@code qtyPerUnit} is the quantity for ONE
     * finished product; {@code orderQty} the order line quantity.
     */
    record BomLineRow(String bomKind, String bomCode, String lineId, String productCode, BigDecimal orderQty,
            Integer lineNo, Integer level, String materialCode, String materialName, String spec, String unit,
            String materialClass, BigDecimal qtyPerUnit, boolean leaf, BigDecimal basePrice) {
    }

    /** One supplier's price for one material. */
    record PriceRow(String materialCode, String supplierCode, String supplierName, BigDecimal price,
            BigDecimal taxRate, String currency, LocalDate validFrom, LocalDate validTo) {
    }
}
