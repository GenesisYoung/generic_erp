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
 * Material prices INCLUDE tax (last purchase ORDSPHS2.PRICETAX, purchase
 * strategy PHSSPROMOT2.PRICE, typed-in prices).
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

    /**
     * One order line (product). {@code materialCost}: its priced BOM lines for
     * the whole line quantity; {@code bomUnitCost}: the BOM cost of ONE
     * finished product (sum of the unit total prices); {@code unitPrice}: the
     * quoted price of one product, calculated from bomUnitCost (see
     * {@link UnitCalc}); {@code totalPrice} = unitPrice x qty. Null on the way
     * in and when the line has no priced material.
     */
    public record OrderLine(String lineId, String productCode, String productName, String spec, BigDecimal qty,
            String unit, String productBomCode, String packagingBomCode, BigDecimal materialCost,
            BigDecimal totalPrice, BigDecimal unitPrice, BigDecimal bomUnitCost) {

        public OrderLine(String lineId, String productCode, String productName, String spec, BigDecimal qty,
                String unit, String productBomCode, String packagingBomCode) {
            this(lineId, productCode, productName, spec, qty, unit, productBomCode, packagingBomCode, null, null,
                    null, null);
        }
    }

    /**
     * The calculation for ONE finished product of an order line: every base
     * amount is per unit. bomUnitCost = productUnitCost + packagingUnitCost
     * (sums of the unit total prices); costBeforeProfit = bomUnitCost +
     * MATERIAL and COST fees; the margin tier is chosen by costBeforeProfit
     * per unit; unitPrice = costBeforeProfit + profit + PRICE fees;
     * totalPrice = unitPrice x qty.
     */
    public record UnitCalc(String lineId, String productCode, String productName, BigDecimal qty,
            BigDecimal productUnitCost, BigDecimal packagingUnitCost, BigDecimal bomUnitCost,
            List<FeeAmount> fees, BigDecimal costBeforeProfit, BigDecimal marginRate, BigDecimal marginTierMin,
            boolean marginOverridden, BigDecimal profit, BigDecimal unitPrice, BigDecimal totalPrice) {
    }

    /** A bought material, or the outsourced processing fee (委外加工费) of a part processed for us. */
    public static final String ITEM_MATERIAL = "MATERIAL";
    public static final String ITEM_PROCESSING = "PROCESSING";

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
    /** Unit price typed in by the purchaser ({@link CalculateRequest#manualPrices}). */
    public static final String PRICE_SOURCE_MANUAL = "MANUAL";

    /**
     * One priced BOM line. {@code kind} PRODUCT or PACKAGING. {@code suppliers}:
     * the last purchase price first (when the material was bought before),
     * then the purchase strategy prices, cheapest first. Without a choice of
     * the purchaser the last purchase price is used, and the cheapest strategy
     * price only when there is none. {@code priceSource} says which one the
     * line uses (LAST_PURCHASE / STRATEGY / MANUAL, null without a price);
     * {@code lastPurchaseDate} is set when it is LAST_PURCHASE.
     * {@code cheapestPrice} is the cheapest strategy price. {@code priceCheck}:
     * OK, HIGH, LOW (selected price outside the band around {@code basePrice},
     * the last purchase price), NO_BASE (no earlier purchase) or NO_PRICE (no
     * price at all). {@code itemType}: MATERIAL (a bought part) or PROCESSING
     * (a part made for us by an outsourced processor from our materials: its
     * sub-parts are priced as materials and this line adds the processing
     * fee). {@code unitTotalPrice} = unitPrice x qtyPerUnit: its cost in ONE
     * finished product.
     */
    public record Item(String kind, String bomCode, String lineId, String productCode, Integer lineNo,
            Integer level,
            String materialCode, String materialName, String spec, String unit, String materialClass,
            BigDecimal qtyPerUnit, BigDecimal orderQty, BigDecimal totalQty, List<SupplierPrice> suppliers,
            String selectedKey, String selectedSupplierCode, String selectedSupplierName, BigDecimal unitPrice,
            BigDecimal amount, BigDecimal cheapestPrice, BigDecimal basePrice, String priceCheck,
            String priceSource, LocalDate lastPurchaseDate, String lastPurchaseNo, String itemType,
            BigDecimal unitTotalPrice) {
    }

    /** {@code amount} = {@code baseAmount} x {@code ratePercent} / 100. */
    public record FeeAmount(String code, String base, BigDecimal ratePercent, BigDecimal baseAmount,
            BigDecimal amount) {
    }

    /**
     * The quotation plan of one order. Fees, margin and price are calculated
     * per finished product of each order line ({@code units}, base amounts per
     * unit). The order figures add them up: fees, costBeforeProfit, profit and
     * totalPrice = sum over the lines of the per-unit amount x qty;
     * materialCost = productMaterialCost + packagingMaterialCost (whole
     * order); unitPrice = totalPrice / total order quantity. marginRate and
     * marginTierMin are those of the first line (each line has its own in
     * {@code units}).
     */
    public record Plan(OrderSummary order, List<OrderLine> lines, List<Item> productItems,
            List<Item> packagingItems, BigDecimal productMaterialCost, BigDecimal packagingMaterialCost,
            BigDecimal materialCost, List<FeeAmount> fees, BigDecimal costBeforeProfit, BigDecimal marginRate,
            BigDecimal marginTierMin, boolean marginOverridden, BigDecimal profit, BigDecimal totalPrice,
            BigDecimal unitPrice, String currency, int missingPriceCount, int priceWarningCount,
            List<String> warnings, LocalDateTime generatedAt, List<UnitCalc> units) {
    }

    /**
     * Changes the purchaser makes on the quotation page. {@code selections}:
     * material code to the {@link SupplierPrice#key} of the price to use
     * (LAST_PURCHASE or a strategy supplier code; missing = last purchase,
     * else cheapest strategy price). {@code manualPrices}: material code to a
     * unit price including tax typed in by the purchaser, for a material
     * without any price; it wins over every other price (0 or less = none). {@code fees}: rate
     * overrides by code for this quotation only. {@code marginRate}: fixed
     * margin instead of the tier (null = tier).
     */
    public record CalculateRequest(Map<String, String> selections, List<Fee> fees, BigDecimal marginRate,
            Map<String, BigDecimal> manualPrices) {

        public CalculateRequest(Map<String, String> selections, List<Fee> fees, BigDecimal marginRate) {
            this(selections, fees, marginRate, null);
        }
    }
}
