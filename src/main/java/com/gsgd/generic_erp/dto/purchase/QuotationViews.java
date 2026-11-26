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

    public record SupplierPrice(String supplierCode, String supplierName, BigDecimal price, BigDecimal taxRate,
            String currency, LocalDate validFrom, LocalDate validTo) {
    }

    /**
     * One priced BOM line. {@code kind} PRODUCT or PACKAGING. {@code suppliers}
     * cheapest first. {@code priceCheck}: OK, HIGH, LOW (selected price
     * outside the band around {@code basePrice}, the last purchase price),
     * NO_BASE (no earlier purchase) or NO_PRICE (no supplier price).
     */
    public record Item(String kind, String bomCode, String productCode, Integer lineNo, Integer level,
            String materialCode, String materialName, String spec, String unit, String materialClass,
            BigDecimal qtyPerUnit, BigDecimal orderQty, BigDecimal totalQty, List<SupplierPrice> suppliers,
            String selectedSupplierCode, String selectedSupplierName, BigDecimal unitPrice, BigDecimal amount,
            BigDecimal cheapestPrice, BigDecimal basePrice, String priceCheck) {
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
     * material code to supplier code (missing = cheapest). {@code fees}: rate
     * overrides by code for this quotation only. {@code marginRate}: fixed
     * margin instead of the tier (null = tier).
     */
    public record CalculateRequest(Map<String, String> selections, List<Fee> fees, BigDecimal marginRate) {
    }
}
