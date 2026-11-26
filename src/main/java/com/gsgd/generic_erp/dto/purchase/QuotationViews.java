package com.gsgd.generic_erp.dto.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request and response shapes of the purchase quotation (报价方案) API. All
 * rates are percentages (2.5 = 2.5%); amounts are in the purchase strategy's
 * currency (one currency per quotation; a warning says when prices mix).
 */
public final class QuotationViews {

    private QuotationViews() {
    }

    /** Fee rates, margin tiers and the unit price check band. */
    public record Settings(List<Fee> fees, List<MarginTier> marginTiers, BigDecimal priceCheckLow,
            BigDecimal priceCheckHigh) {
    }

    /** One additional fee: code (QuotationFeeCode), base (QuotationFeeBase), rate. */
    public record Fee(String code, String base, BigDecimal ratePercent, boolean enabled) {
    }

    /** Margin used when the cost before profit is at least {@code minAmount}. */
    public record MarginTier(BigDecimal minAmount, BigDecimal ratePercent) {
    }

    /**
     * BOM status of an order: BOTH (product + packaging BOM), PRODUCT_ONLY,
     * PACKAGING_ONLY or NONE.
     */
    public record OrderSummary(String orderNo, String customerCode, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, String planNo, LocalDate planDate, int lineCount, BigDecimal totalQty,
            boolean hasProductBom, boolean hasPackagingBom, String bomStatus, int planLines,
            int planLinesBought) {
    }

    public record OrderLine(String lineId, String productCode, String productName, String spec, BigDecimal qty,
            String unit, String productBomCode, String packagingBomCode) {
    }

    /**
     * One price a material can be bought at. {@code source} LAST_PURCHASE (the
     * material's most recent purchase order line: {@code purchaseDate},
     * {@code poNo}) or STRATEGY (purchase strategy 采购策略: {@code validFrom} /
     * {@code validTo}). {@code key} is what {@link CalculateRequest#selections}
     * names to choose it: {@link #LAST_PURCHASE_KEY} or the supplier code of a
     * strategy price.
     */
    public record SupplierPrice(String key, String source, String supplierCode, String supplierName,
            BigDecimal price, BigDecimal taxRate, String currency, LocalDate validFrom, LocalDate validTo,
            LocalDate purchaseDate, String poNo) {

        public static final String LAST_PURCHASE_KEY = "LAST_PURCHASE";

        /** A purchase strategy price. */
        public static SupplierPrice strategy(String supplierCode, String supplierName, BigDecimal price,
                BigDecimal taxRate, String currency, LocalDate validFrom, LocalDate validTo) {
            return new SupplierPrice(supplierCode, PRICE_SOURCE_STRATEGY, supplierCode, supplierName, price,
                    taxRate, currency, validFrom, validTo, null, null);
        }
    }

    public static final String PRICE_SOURCE_LAST_PURCHASE = "LAST_PURCHASE";
    public static final String PRICE_SOURCE_STRATEGY = "STRATEGY";

    /**
     * One priced BOM line. {@code kind} PRODUCT or PACKAGING. {@code suppliers}:
     * the last purchase price first (when the material was bought before),
     * then the purchase strategy prices, cheapest first. Without a choice of
     * the purchaser the last purchase price is used, and the cheapest strategy
     * price only when there is none. {@code priceSource} says which one the
     * line uses (LAST_PURCHASE / STRATEGY, null without a price);
     * {@code lastPurchaseDate} is set when it is LAST_PURCHASE.
     * {@code cheapestPrice} is the cheapest strategy price. {@code priceCheck}:
     * OK, HIGH, LOW (selected price outside the band around {@code basePrice},
     * the last purchase price), NO_BASE (no earlier purchase) or NO_PRICE (no
     * price at all).
     */
    public record Item(String kind, String bomCode, String productCode, Integer lineNo, Integer level,
            String materialCode, String materialName, String spec, String unit, String materialClass,
            BigDecimal qtyPerUnit, BigDecimal orderQty, BigDecimal totalQty, List<SupplierPrice> suppliers,
            String selectedKey, String selectedSupplierCode, String selectedSupplierName, BigDecimal unitPrice,
            BigDecimal amount, BigDecimal cheapestPrice, BigDecimal basePrice, String priceCheck,
            String priceSource, LocalDate lastPurchaseDate, String lastPurchaseNo) {
    }

    /** {@code amount} = {@code baseAmount} x {@code ratePercent} / 100. */
    public record FeeAmount(String code, String base, BigDecimal ratePercent, BigDecimal baseAmount,
            BigDecimal amount) {
    }

    /**
     * The quotation plan of one order.
     * <p>
     * materialCost = productMaterialCost + packagingMaterialCost;
     * costBeforeProfit = materialCost + MATERIAL and COST fees;
     * profit = costBeforeProfit x marginRate;
     * totalPrice = costBeforeProfit + profit + PRICE fees (tax);
     * unitPrice = totalPrice / total order quantity.
     */
    public record Plan(OrderSummary order, List<OrderLine> lines, List<Item> productItems,
            List<Item> packagingItems, BigDecimal productMaterialCost, BigDecimal packagingMaterialCost,
            BigDecimal materialCost, List<FeeAmount> fees, BigDecimal costBeforeProfit, BigDecimal marginRate,
            BigDecimal marginTierMin, boolean marginOverridden, BigDecimal profit, BigDecimal totalPrice,
            BigDecimal unitPrice, String currency, int missingPriceCount, int priceWarningCount,
            List<String> warnings, LocalDateTime generatedAt) {
    }

    /**
     * Changes the purchaser makes on the quotation page. {@code selections}:
     * material code to the {@link SupplierPrice#key} of the price to use
     * (LAST_PURCHASE or a strategy supplier code; missing = last purchase,
     * else cheapest strategy price). {@code fees}: rate
     * overrides by code for this quotation only. {@code marginRate}: fixed
     * margin instead of the tier (null = tier).
     */
    public record CalculateRequest(Map<String, String> selections, List<Fee> fees, BigDecimal marginRate) {
    }
}
