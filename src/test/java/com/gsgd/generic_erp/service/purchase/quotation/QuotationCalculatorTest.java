package com.gsgd.generic_erp.service.purchase.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.Fee;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.FeeAmount;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Item;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderLine;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderSummary;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.enums.QuotationFeeCode;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.BomLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.LastPurchase;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.PriceRow;

/**
 * Worked example (order qty 100):
 * product A 2/unit x 1.50 = 300, B 1/unit x 2.00 = 200 -> product 500;
 * packaging C (class 302) 1/unit x 0.50 = 50 -> material cost 550;
 * fees 0.5 + 2.5 + 15 + 1 + 2 = 21% of 550 = 115.50 -> cost 665.50;
 * below 3,000 -> 15% profit = 99.83; tax 6% of 765.33 = 45.92; total 811.25.
 */
class QuotationCalculatorTest {

    @Test
    void taxFeeDefaultsToZeroBecausePricesIncludeTax() {
        assertEquals(0, BigDecimal.ZERO.compareTo(QuotationFeeCode.TAX.defaultRate()));
    }

    static final Locale EN = Locale.US;

    /** The purchasing department's original rates (tax 6%), so the worked examples keep a PRICE fee. */
    static Settings defaults() {
        List<Fee> fees = java.util.Arrays.stream(QuotationFeeCode.values())
                .map(c -> new Fee(c.name(), c.defaultBase().name(),
                        c == QuotationFeeCode.TAX ? new BigDecimal("6") : c.defaultRate(), true)).toList();
        return new Settings(fees, QuotationSettingsService.DEFAULT_TIERS, new BigDecimal("0.8"),
                new BigDecimal("1.2"));
    }

    static BomLineRow bom(String kind, String material, String clas, String perUnit, boolean leaf, String base) {
        return new BomLineRow(kind, "BOM-1", "L1", "P-1", null, 1, 1, material, material + " name", "spec", "PCS",
                clas, new BigDecimal(perUnit), leaf, base == null ? null : new BigDecimal(base), null, null);
    }

    static PriceRow price(String material, String supplier, String price) {
        return new PriceRow(material, supplier, supplier + " Ltd", new BigDecimal(price), new BigDecimal("13"),
                "RMB", LocalDate.of(2026, 1, 1), null);
    }

    static Plan calc(String orderQty, Map<String, String> selections, Settings settings, BigDecimal margin) {
        OrderSummary order = new OrderSummary("26394-W53", "C1", "Customer", LocalDate.of(2026, 11, 16), null,
                "26394-W53", LocalDate.of(2026, 11, 16), 1, new BigDecimal(orderQty), true, false, "PRODUCT_ONLY",
                0, 0);
        List<OrderLine> lines = List.of(new OrderLine("L1", "P-1", "Lamp", null, new BigDecimal(orderQty), "PCS",
                "BOM-1", null));
        List<BomLineRow> bom = List.of(
                bom(null, "KIT", "603", "1", false, null), // has sub-lines: not priced
                bom(null, "A", "202", "2", true, "1.00"),
                bom(null, "B", "210", "1", true, "2.00"),
                bom(null, "C", "302", "1", true, null));
        List<PriceRow> prices = List.of(price("A", "S2", "1.80"), price("A", "S1", "1.50"),
                price("A", "S1", "1.60"), price("B", "S3", "2.00"), price("C", "S4", "0.50"),
                price("KIT", "S9", "99"));
        return QuotationCalculator.calculate(new QuotationCalculator.Input(order, lines, bom, prices, settings,
                selections, margin, List.of("3"), LocalDateTime.of(2026, 11, 25, 18, 0), EN));
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void workedExampleBelowThreeThousandUsesFifteenPercent() {
        Plan p = calc("100", Map.of(), defaults(), null);

        assertEquals(2, p.productItems().size(), "the kit with sub-lines is not priced");
        assertEquals(1, p.packagingItems().size(), "class 3xx counts as packaging");
        Item a = p.productItems().get(0);
        assertEquals("S1", a.selectedSupplierCode(), "cheapest supplier by default");
        assertEquals(0, bd("1.50").compareTo(a.unitPrice()), "lowest price of S1 is kept");
        assertEquals(2, a.suppliers().size(), "one price per supplier");
        assertEquals(0, bd("200").compareTo(a.totalQty()));
        assertEquals(0, bd("300").compareTo(a.amount()));
        assertEquals("HIGH", a.priceCheck(), "1.50 / 1.00 >= 1.2");
        assertEquals("OK", p.productItems().get(1).priceCheck());
        assertEquals("NO_BASE", p.packagingItems().get(0).priceCheck());

        assertEquals(bd("500.00"), p.productMaterialCost());
        assertEquals(bd("50.00"), p.packagingMaterialCost());
        assertEquals(bd("550.00"), p.materialCost());
        FeeAmount mgmt = p.fees().stream().filter(f -> f.code().equals("MANAGEMENT")).findFirst().orElseThrow();
        assertEquals(bd("82.50"), mgmt.amount());
        assertEquals(bd("665.50"), p.costBeforeProfit());
        assertEquals(0, bd("15").compareTo(p.marginRate()));
        assertEquals(bd("99.83"), p.profit());
        FeeAmount tax = p.fees().get(p.fees().size() - 1);
        assertEquals("TAX", tax.code());
        assertEquals(bd("765.33"), tax.baseAmount());
        assertEquals(bd("45.92"), tax.amount());
        assertEquals(bd("811.25"), p.totalPrice());
        assertEquals(bd("8.1125"), p.unitPrice());
        assertEquals("RMB", p.currency());
        assertEquals(1, p.priceWarningCount());
        assertEquals(0, p.missingPriceCount());
    }

    @Test
    void tierIsChosenByTheCostPerUnitNotByTheOrderTotal() {
        // 1000 products: 6,655 before profit for the order, but 6.655 per unit -> lowest tier (15%).
        Plan p = calc("1000", Map.of(), defaults(), null);
        assertEquals(bd("6655.00"), p.costBeforeProfit());
        assertEquals(0, bd("15").compareTo(p.marginRate()));
        assertEquals(1, p.units().size());
        var u = p.units().get(0);
        assertEquals(bd("5.5000"), u.bomUnitCost(), "BOM cost of one product: 2 x 1.50 + 2.00 + 0.50");
        assertEquals(bd("6.6550"), u.costBeforeProfit());
        assertEquals(bd("5.5000"), u.fees().get(0).baseAmount(), "fee base = BOM cost per unit");
        assertEquals(0, u.unitPrice().multiply(bd("1000")).compareTo(u.totalPrice()));
        assertEquals(u.totalPrice(), p.totalPrice());

        // A product whose cost before profit reaches 3,000 PER UNIT gets 10%.
        List<BomLineRow> dear = List.of(bom(null, "D", "202", "1", true, null));
        Plan big = calcRows(dear, List.of(price("D", "S1", "2500")), Map.of());
        var bu = big.units().get(0);
        assertEquals(0, bd("3025.0000").compareTo(bu.costBeforeProfit()), "2500 + 21% fees");
        assertEquals(0, bd("10").compareTo(bu.marginRate()));
        assertEquals(0, bd("3000").compareTo(bu.marginTierMin()));
    }

    @Test
    void unitTotalPriceIsPriceTimesUsageAndAddsUpToTheBomCostPerUnit() {
        Plan p = calc("100", Map.of(), defaults(), null);
        Item a = p.productItems().get(0);
        assertEquals(bd("3.000000"), a.unitTotalPrice(), "1.50 x 2 per product");
        assertEquals(bd("2.000000"), p.productItems().get(1).unitTotalPrice());
        assertEquals(bd("0.500000"), p.packagingItems().get(0).unitTotalPrice());
        var u = p.units().get(0);
        assertEquals(bd("5.0000"), u.productUnitCost());
        assertEquals(bd("0.5000"), u.packagingUnitCost());
        assertEquals(bd("5.5000"), p.lines().get(0).bomUnitCost());
        assertEquals(u.unitPrice(), p.lines().get(0).unitPrice());
    }

    @Test
    void outsourcedProcessingPartAddsItsProcessingFeeOnTopOfItsMaterials() {
        BomLineRow part = new BomLineRow(null, "B", "L1", "P-1", null, 1, 1, "501-X", "frame", null, "PCS", "501",
                bd("2"), false, null, "P1", null, null, true);
        BomLineRow child = new BomLineRow(null, "B", "L1", "P-1", null, 2, 2, "201-Y", "steel", null, "PCS", "201",
                bd("1"), true, null, "P2", "P1");
        BomLineRow selfMade = new BomLineRow(null, "B", "L1", "P-1", null, 3, 1, "603-Z", "kit", null, "PCS", "603",
                bd("1"), false, null, "P3", null, null, false);
        List<PriceRow> prices = List.of(
                new PriceRow("501-X", "5051", "Processor", bd("1.00"), bd("13"), "RMB", null, null, true),
                new PriceRow("501-X", "S9", "Seller", bd("9.00"), bd("13"), "RMB", null, null, false),
                price("201-Y", "S1", "0.50"));
        Plan p = calcRows(List.of(part, child, selfMade), prices, Map.of());

        assertEquals(2, p.productItems().size(), "processed part + its material; the in-house kit is not priced");
        Item fee = p.productItems().get(0);
        assertEquals("PROCESSING", fee.itemType());
        assertEquals("5051", fee.selectedSupplierCode(), "only the processing fee, not the whole part");
        assertEquals(1, fee.suppliers().size());
        assertEquals(bd("2.000000"), fee.unitTotalPrice(), "1.00 x 2 per product");
        Item steel = p.productItems().get(1);
        assertEquals("MATERIAL", steel.itemType());
        assertEquals(bd("1.000000"), steel.unitTotalPrice(), "0.50 x (1 x 2) per product");
        assertEquals(bd("3.0000"), p.units().get(0).bomUnitCost());
    }

    @Test
    void purchaserCanPickAnotherSupplierAndMargin() {
        Plan p = calc("100", Map.of("A", "S2", "B", "NOPE"), defaults(), new BigDecimal("12"));
        Item a = p.productItems().get(0);
        assertEquals("S2", a.selectedSupplierCode());
        assertEquals(0, bd("360").compareTo(a.amount()));
        assertEquals(0, bd("1.50").compareTo(a.cheapestPrice()));
        assertEquals("S3", p.productItems().get(1).selectedSupplierCode(), "unknown choice falls back");
        assertTrue(p.marginOverridden());
        assertNull(p.marginTierMin());
        assertTrue(p.warnings().stream().anyMatch(w -> w.contains("B")));
    }

    @Test
    void disabledFeesAndMissingPrices() {
        Settings s = defaults();
        List<Fee> fees = s.fees().stream()
                .map(f -> new Fee(f.code(), f.base(), f.ratePercent(), f.code().equals("TAX"))).toList();
        Settings onlyTax = new Settings(fees, s.marginTiers(), s.priceCheckLow(), s.priceCheckHigh());
        OrderSummary order = new OrderSummary("X", null, null, null, null, null, null, 1, BigDecimal.TEN, false,
                false, "NONE", 0, 0);
        Plan p = QuotationCalculator.calculate(new QuotationCalculator.Input(order,
                List.of(new OrderLine("L1", "P", null, null, BigDecimal.TEN, null, null, null)),
                List.of(bom("PACKAGING", "Z", "202", "3", true, null)), List.of(), onlyTax, null, null,
                List.of("3"), LocalDateTime.now(), EN));
        assertEquals(1, p.fees().size());
        assertEquals(1, p.missingPriceCount());
        assertEquals("NO_PRICE", p.packagingItems().get(0).priceCheck(), "bom kind wins over class");
        assertEquals(bd("0.00"), p.totalPrice());
        assertFalse(p.warnings().isEmpty());
    }

    @Test
    void tierBelowEveryMinimumIsTheLowestTier() {
        var tiers = List.of(new com.gsgd.generic_erp.dto.purchase.QuotationViews.MarginTier(bd("1000"), bd("15")),
                new com.gsgd.generic_erp.dto.purchase.QuotationViews.MarginTier(bd("3000"), bd("10")));
        assertEquals(bd("15"), QuotationCalculator.tierFor(bd("10"), tiers).ratePercent());
        assertEquals(bd("15"), QuotationCalculator.tierFor(bd("2999.99"), tiers).ratePercent());
        assertEquals(bd("10"), QuotationCalculator.tierFor(bd("3000"), tiers).ratePercent());
    }

    @Test
    void priceCheckBand() {
        BigDecimal low = bd("0.8");
        BigDecimal high = bd("1.2");
        assertEquals("OK", QuotationCalculator.priceCheck(bd("1.19"), bd("1"), low, high));
        assertEquals("HIGH", QuotationCalculator.priceCheck(bd("1.2"), bd("1"), low, high));
        assertEquals("LOW", QuotationCalculator.priceCheck(bd("0.8"), bd("1"), low, high));
        assertEquals("NO_BASE", QuotationCalculator.priceCheck(bd("1"), BigDecimal.ZERO, low, high));
        assertEquals("NO_PRICE", QuotationCalculator.priceCheck(null, bd("1"), low, high));
    }

    @Test
    void qtyPerProductMultipliesUpTheTreeAndStopsOnLoops() {
        BomLineRow top = new BomLineRow(null, "B", "L1", "P", null, 1, 1, "S1", "n", null, "PCS", "202",
                new BigDecimal("2"), false, null, "E1", null);
        BomLineRow mid = new BomLineRow(null, "B", "L1", "P", null, 2, 2, "S2", "n", null, "PCS", "202",
                new BigDecimal("3"), false, null, "E2", "E1");
        BomLineRow leaf = new BomLineRow(null, "B", "L1", "P", null, 3, 3, "M1", "n", null, "PCS", "202",
                new BigDecimal("0.5"), true, null, "E3", "E2");
        BomLineRow orphan = new BomLineRow(null, "B", "L1", "P", null, 4, 2, "M2", "n", null, "PCS", "202",
                new BigDecimal("4"), true, null, "E4", "MISSING");
        BomLineRow loopA = new BomLineRow(null, "B", "L1", "P", null, 5, 2, "M3", "n", null, "PCS", "202",
                new BigDecimal("2"), true, null, "E5", "E6");
        BomLineRow loopB = new BomLineRow(null, "B", "L1", "P", null, 6, 2, "M4", "n", null, "PCS", "202",
                new BigDecimal("5"), true, null, "E6", "E5");
        var q = QuotationCalculator.qtyPerProduct(List.of(top, mid, leaf, orphan, loopA, loopB));
        assertEquals(0, new BigDecimal("3").compareTo(q.get("E3")));
        assertEquals(0, new BigDecimal("6").compareTo(q.get("E2")));
        assertEquals(0, new BigDecimal("4").compareTo(q.get("E4")));
        assertEquals(0, new BigDecimal("10").compareTo(q.get("E5")));
    }

    static BomLineRow bought(String material, String base, String supplier, LocalDate date) {
        return new BomLineRow(null, "BOM-1", "L1", "P-1", null, 1, 1, material, material + " name", "spec", "PCS",
                "202", BigDecimal.ONE, true, new BigDecimal(base), null, null,
                new LastPurchase(supplier, supplier + " Ltd", date, "P2611", new BigDecimal("13"), "RMB"));
    }

    static Plan calcRows(List<BomLineRow> bom, List<PriceRow> prices, Map<String, String> selections) {
        OrderSummary order = new OrderSummary("O", null, null, null, null, null, null, 1, BigDecimal.TEN, true,
                false, "PRODUCT_ONLY", 0, 0);
        List<OrderLine> lines = List.of(new OrderLine("L1", "P-1", "Lamp", null, BigDecimal.TEN, "PCS", "BOM-1",
                null));
        return QuotationCalculator.calculate(new QuotationCalculator.Input(order, lines, bom, prices, defaults(),
                selections, null, List.of("3"), LocalDateTime.of(2026, 11, 26, 9, 0), EN));
    }

    @Test
    void lastPurchasePriceComesFirstEvenWhenAStrategyPriceIsLower() {
        LocalDate day = LocalDate.of(2026, 10, 3);
        Plan p = calcRows(List.of(bought("A", "1.70", "S7", day)),
                List.of(price("A", "S1", "1.50"), price("A", "S2", "1.80")), Map.of());

        Item a = p.productItems().get(0);
        assertEquals("LAST_PURCHASE", a.priceSource());
        assertEquals("LAST_PURCHASE", a.selectedKey());
        assertEquals("S7", a.selectedSupplierCode(), "supplier of the last purchase");
        assertEquals(0, bd("1.70").compareTo(a.unitPrice()));
        assertEquals(day, a.lastPurchaseDate());
        assertEquals("P2611", a.lastPurchaseNo());
        assertEquals(3, a.suppliers().size(), "last purchase + two strategy prices");
        assertEquals("LAST_PURCHASE", a.suppliers().get(0).source());
        assertEquals(day, a.suppliers().get(0).purchaseDate());
        assertEquals("STRATEGY", a.suppliers().get(1).source());
        assertEquals(0, bd("1.50").compareTo(a.cheapestPrice()), "cheapest strategy price still shown");
        assertEquals("OK", a.priceCheck());
        assertEquals(bd("17.00"), p.productMaterialCost());
    }

    @Test
    void strategyPriceOnlyWhenTheMaterialWasNeverBought() {
        Plan p = calcRows(List.of(bom(null, "N", "202", "1", true, null)),
                List.of(price("N", "S2", "1.80"), price("N", "S1", "1.50")), Map.of());

        Item n = p.productItems().get(0);
        assertEquals("STRATEGY", n.priceSource());
        assertEquals("S1", n.selectedSupplierCode(), "best strategy price");
        assertEquals("S1", n.selectedKey());
        assertNull(n.lastPurchaseDate());
        assertEquals("NO_BASE", n.priceCheck());
    }

    @Test
    void purchaserCanPickAStrategyPriceOfTheSameSupplierOrGoBack() {
        LocalDate day = LocalDate.of(2026, 10, 3);
        List<BomLineRow> bom = List.of(bought("A", "1.70", "S1", day));
        List<PriceRow> prices = List.of(price("A", "S1", "1.50"));

        Item picked = calcRows(bom, prices, Map.of("A", "S1")).productItems().get(0);
        assertEquals("STRATEGY", picked.priceSource());
        assertEquals(0, bd("1.50").compareTo(picked.unitPrice()));
        assertNull(picked.lastPurchaseDate());

        Item back = calcRows(bom, prices, Map.of("A", "LAST_PURCHASE")).productItems().get(0);
        assertEquals("LAST_PURCHASE", back.priceSource());
        assertEquals(0, bd("1.70").compareTo(back.unitPrice()));
    }

    @Test
    void noPriceWithoutPurchaseOrStrategy() {
        Item x = calcRows(List.of(bom(null, "X", "202", "1", true, null)), List.of(), Map.of()).productItems()
                .get(0);
        assertNull(x.priceSource());
        assertEquals("NO_PRICE", x.priceCheck());
    }

    @Test
    void manualPriceFillsAMaterialWithoutAnyPrice() {
        List<BomLineRow> bom = List.of(bom(null, "X", "202", "2", true, null));
        Plan p = QuotationCalculator.calculate(new QuotationCalculator.Input(
                new OrderSummary("O", null, null, null, null, null, null, 1, BigDecimal.TEN, true, false,
                        "PRODUCT_ONLY", 0, 0),
                List.of(new OrderLine("L1", "P-1", "Lamp", null, BigDecimal.TEN, "PCS", "BOM-1", null)), bom,
                List.of(), defaults(), Map.of(), null, List.of("3"), LocalDateTime.of(2026, 11, 26, 9, 0), EN,
                Map.of("X", bd("0.75"))));

        Item x = p.productItems().get(0);
        assertEquals("MANUAL", x.priceSource());
        assertEquals(0, bd("0.75").compareTo(x.unitPrice()));
        assertEquals(bd("15.0000"), x.amount(), "2 x 10 x 0.75");
        assertEquals(0, p.missingPriceCount());
        assertTrue(p.warnings().stream().anyMatch(w -> w.contains("X")), "manual prices are listed");
    }

    @Test
    void eachOrderLineGetsItsShareOfTheTotalAndAUnitPrice() {
        BomLineRow a = new BomLineRow(null, "B1", "L1", "P-1", null, 1, 1, "A", "a", null, "PCS", "202",
                BigDecimal.ONE, true, null, null, null);
        BomLineRow b = new BomLineRow(null, "B2", "L2", "P-2", null, 1, 1, "B", "b", null, "PCS", "202",
                BigDecimal.ONE, true, null, null, null);
        List<OrderLine> lines = List.of(
                new OrderLine("L1", "P-1", "Lamp", null, bd("100"), "PCS", "B1", null),
                new OrderLine("L2", "P-2", "Base", null, bd("50"), "PCS", "B2", null),
                new OrderLine("L3", "P-3", "No BOM", null, bd("5"), "PCS", null, null));
        Plan p = QuotationCalculator.calculate(new QuotationCalculator.Input(
                new OrderSummary("O", null, null, null, null, null, null, 3, bd("155"), true, false,
                        "PRODUCT_ONLY", 0, 0),
                lines, List.of(a, b), List.of(price("A", "S1", "3"), price("B", "S1", "2")), defaults(), Map.of(),
                null, List.of("3"), LocalDateTime.of(2026, 11, 26, 9, 0), EN));

        // material 300 + 100 = 400; every fee, profit and tax is a percentage, so L1 gets 3/4 of the total.
        OrderLine l1 = p.lines().get(0);
        OrderLine l2 = p.lines().get(1);
        assertEquals(bd("300.00"), l1.materialCost());
        assertEquals(p.totalPrice().multiply(bd("0.75")).setScale(2, java.math.RoundingMode.HALF_UP),
                l1.totalPrice());
        assertEquals(p.totalPrice().multiply(bd("0.75")).divide(bd("100"), 4, java.math.RoundingMode.HALF_UP),
                l1.unitPrice());
        assertEquals(p.totalPrice().divide(bd("4"), 2, java.math.RoundingMode.HALF_UP), l2.totalPrice());
        assertNull(p.lines().get(2).totalPrice(), "no priced material");
        assertNull(p.lines().get(2).unitPrice());
    }
}
