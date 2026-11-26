package com.gsgd.generic_erp.service.purchase.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.QuotationViews;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Fee;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.FeeAmount;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Item;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.MarginTier;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderLine;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderSummary;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.SupplierPrice;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.UnitCalc;
import com.gsgd.generic_erp.enums.QuotationFeeBase;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.BomLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.LastPurchase;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.PriceRow;

/**
 * Pure calculation of a quotation plan (no database, no Spring), so every rule
 * is unit-tested.
 *
 * <ol>
 * <li>Only leaf BOM lines are priced (a part whose sub-parts are listed would
 * otherwise count twice). Each line is PRODUCT or PACKAGING: the BOM it comes
 * from decides, or, when the BOM does not say, the material class (classes
 * starting with a packaging prefix, "3" by default).</li>
 * <li>Each line takes the material's most recent purchase price (supplier and
 * price of its last purchase order line); only a material that was never
 * bought takes the cheapest valid purchase strategy price. The purchaser can
 * pick another price for a material. The line says which source it uses and,
 * for a last purchase price, the purchase date. Amount = quantity per product
 * x order quantity x unit price. A material without any price can be given
 * a unit price by hand (source MANUAL).</li>
 * <li>A part with sub-parts that an outsourced processor makes for us
 * (委外加工) is priced too: its sub-parts as materials, the part itself at its
 * processing fee (item type PROCESSING).</li>
 * <li>Unit total price of a line = unit price x usage per finished product;
 * their sum per order line is the BOM cost of one finished product.</li>
 * <li>Everything below is calculated per finished product of each order line,
 * the base amounts being per unit: MATERIAL fees are a percentage of the BOM
 * cost per unit, COST fees of that plus MATERIAL fees; their sum is the cost
 * before profit per unit.</li>
 * <li>Profit margin: the tier with the highest minimum the cost before profit
 * PER UNIT reaches (below every minimum: the lowest tier), unless
 * overridden.</li>
 * <li>PRICE fees (tax) are a percentage of cost before profit plus profit.
 * Unit price = cost before profit + profit + PRICE fees; line total = unit
 * price x quantity; order total = sum of the line totals.</li>
 * </ol>
 */
public final class QuotationCalculator {

    public static final String KIND_PRODUCT = "PRODUCT";
    public static final String KIND_PACKAGING = "PACKAGING";

    static final int MONEY = 2;
    static final int LINE = 4;
    /** Unit total price (price x usage per product) keeps 6 decimals; small parts cost fractions of a cent. */
    static final int UNIT = 6;

    private QuotationCalculator() {
    }

    /** Everything one calculation needs. */
    public record Input(OrderSummary order, List<OrderLine> lines, List<BomLineRow> bomLines,
            List<PriceRow> prices, Settings settings, Map<String, String> selections, BigDecimal marginOverride,
            List<String> packagingClassPrefixes, LocalDateTime now, Locale locale,
            Map<String, BigDecimal> manualPrices) {

        public Input(OrderSummary order, List<OrderLine> lines, List<BomLineRow> bomLines, List<PriceRow> prices,
                Settings settings, Map<String, String> selections, BigDecimal marginOverride,
                List<String> packagingClassPrefixes, LocalDateTime now, Locale locale) {
            this(order, lines, bomLines, prices, settings, selections, marginOverride, packagingClassPrefixes, now,
                    locale, null);
        }
    }

    public static Plan calculate(Input in) {
        Locale locale = in.locale() == null ? I18n.currentLocale() : in.locale();
        List<String> warnings = new ArrayList<>();
        Map<String, List<SupplierPrice>> suppliers = suppliersByMaterial(
                in.prices().stream().filter(p -> !p.processing()).toList());
        Map<String, List<SupplierPrice>> processors = suppliersByMaterial(
                in.prices().stream().filter(PriceRow::processing).toList());
        Map<String, BigDecimal> lineQty = new HashMap<>();
        BigDecimal totalOrderQty = BigDecimal.ZERO;
        for (OrderLine l : in.lines()) {
            if (l.lineId() != null && l.qty() != null) {
                lineQty.put(l.lineId(), l.qty());
            }
            totalOrderQty = totalOrderQty.add(nz(l.qty()));
        }
        Map<String, String> selections = in.selections() == null ? Map.of() : in.selections();
        Map<String, BigDecimal> manual = in.manualPrices() == null ? Map.of() : in.manualPrices();
        Set<String> manualLines = new LinkedHashSet<>();
        BigDecimal low = in.settings().priceCheckLow();
        BigDecimal high = in.settings().priceCheckHigh();

        Map<String, BigDecimal> perProduct = qtyPerProduct(in.bomLines());
        List<Item> product = new ArrayList<>();
        List<Item> packaging = new ArrayList<>();
        Set<String> currencies = new LinkedHashSet<>();
        Set<String> badSelections = new LinkedHashSet<>();
        int missing = 0;
        int priceWarnings = 0;
        for (BomLineRow row : in.bomLines()) {
            boolean processing = row.processing() && !row.leaf();
            if (row.materialCode() == null || !(row.leaf() || processing)) {
                continue;
            }
            String kind = kindOf(row, in.packagingClassPrefixes());
            BigDecimal orderQty = row.orderQty() != null ? row.orderQty() : lineQty.get(row.lineId());
            BigDecimal perUnit = row.lineKey() != null && perProduct.containsKey(row.lineKey())
                    ? perProduct.get(row.lineKey()) : nz(row.qtyPerUnit());
            BigDecimal totalQty = perUnit.multiply(nz(orderQty));
            // A processed part: only its processing fee (its sub-parts are priced as materials). A bought
            // part: its purchase prices, or a processing fee when that is all the strategy has.
            List<SupplierPrice> strategy = processing ? processors.getOrDefault(row.materialCode(), List.of())
                    : suppliers.getOrDefault(row.materialCode(),
                            processors.getOrDefault(row.materialCode(), List.of()));
            SupplierPrice last = processing ? null : lastPurchaseOffer(row);
            List<SupplierPrice> offers = new ArrayList<>();
            if (last != null) {
                offers.add(last);
            }
            offers.addAll(strategy);
            // Rule: the last purchase price first; the cheapest strategy price only without one.
            SupplierPrice chosen = offers.isEmpty() ? null : offers.get(0);
            String wanted = selections.get(row.materialCode());
            if (wanted != null && !wanted.isBlank()) {
                SupplierPrice match = offers.stream().filter(o -> wanted.equals(o.key())).findFirst().orElse(null);
                if (match != null) {
                    chosen = match;
                } else {
                    badSelections.add(row.materialCode());
                }
            }
            BigDecimal typed = manual.get(row.materialCode());
            if (typed != null && typed.signum() > 0) {
                // Typed in by the purchaser for a material without a price: wins over everything else.
                chosen = new SupplierPrice(QuotationViews.PRICE_SOURCE_MANUAL, QuotationViews.PRICE_SOURCE_MANUAL,
                        null, null, typed, null, null, null, null, null, null);
                manualLines.add(row.materialCode());
            }
            BigDecimal unitPrice = chosen == null ? null : chosen.price();
            BigDecimal amount = unitPrice == null ? BigDecimal.ZERO
                    : totalQty.multiply(unitPrice).setScale(LINE, RoundingMode.HALF_UP);
            String check = processing && chosen != null ? "OK"
                    : priceCheck(unitPrice, row.basePrice(), low, high);
            if (chosen == null) {
                missing++;
            } else {
                if (chosen.currency() != null) {
                    currencies.add(chosen.currency());
                }
                if ("HIGH".equals(check) || "LOW".equals(check)) {
                    priceWarnings++;
                }
            }
            boolean fromLast = chosen != null && chosen == last;
            Item item = new Item(kind, row.bomCode(), row.lineId(), row.productCode(), row.lineNo(), row.level(),
                    row.materialCode(), row.materialName(), row.spec(), row.unit(), row.materialClass(), perUnit,
                    orderQty, totalQty, offers, chosen == null ? null : chosen.key(),
                    chosen == null ? null : chosen.supplierCode(), chosen == null ? null : chosen.supplierName(),
                    unitPrice, amount, strategy.isEmpty() ? null : strategy.get(0).price(), row.basePrice(), check,
                    chosen == null ? null : chosen.source(), fromLast ? last.purchaseDate() : null,
                    fromLast ? last.poNo() : null,
                    processing ? QuotationViews.ITEM_PROCESSING : QuotationViews.ITEM_MATERIAL,
                    unitPrice == null ? null : unitPrice.multiply(perUnit).setScale(UNIT, RoundingMode.HALF_UP));
            (KIND_PACKAGING.equals(kind) ? packaging : product).add(item);
        }

        BigDecimal productCost = money(sum(product));
        BigDecimal packagingCost = money(sum(packaging));
        BigDecimal materialCost = productCost.add(packagingCost);

        // Fees, margin and price per finished product of each order line (base amounts per unit).
        List<Fee> fees = in.settings().fees().stream().filter(Fee::enabled).toList();
        List<UnitCalc> units = new ArrayList<>();
        List<OrderLine> lines = new ArrayList<>();
        for (OrderLine l : linesWithItems(in.lines(), product, packaging)) {
            BigDecimal productUnit = unitCost(product, l.lineId());
            BigDecimal packagingUnit = unitCost(packaging, l.lineId());
            BigDecimal lineMaterial = lineAmount(product, l.lineId()).add(lineAmount(packaging, l.lineId()));
            if (productUnit == null && packagingUnit == null) {
                lines.add(withPrices(l, null, null, null, null));
                continue;
            }
            BigDecimal bomUnit = nz(productUnit).add(nz(packagingUnit));
            UnitCalc u = perUnit(l, nz(productUnit), nz(packagingUnit), bomUnit, fees,
                    in.settings().marginTiers(), in.marginOverride());
            units.add(u);
            lines.add(withPrices(l, money(lineMaterial), u.totalPrice(), u.unitPrice(), u.bomUnitCost()));
        }

        Map<String, FeeAmount> feeTotals = new LinkedHashMap<>();
        BigDecimal costBeforeProfit = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (UnitCalc u : units) {
            BigDecimal q = nz(u.qty());
            for (FeeAmount f : u.fees()) {
                FeeAmount scaled = new FeeAmount(f.code(), f.base(), f.ratePercent(), money(f.baseAmount().multiply(q)),
                        money(f.amount().multiply(q)));
                feeTotals.merge(f.code(), scaled, (x, y) -> new FeeAmount(x.code(), x.base(), x.ratePercent(),
                        x.baseAmount().add(y.baseAmount()), x.amount().add(y.amount())));
            }
            costBeforeProfit = costBeforeProfit.add(money(u.costBeforeProfit().multiply(q)));
            profit = profit.add(money(u.profit().multiply(q)));
            total = total.add(u.totalPrice());
        }
        List<FeeAmount> feeAmounts = new ArrayList<>(feeTotals.values());
        if (units.isEmpty()) {
            for (Fee f : fees) {
                feeAmounts.add(new FeeAmount(f.code(), f.base(), f.ratePercent(), money(BigDecimal.ZERO),
                        money(BigDecimal.ZERO)));
            }
        }
        boolean overridden = in.marginOverride() != null;
        UnitCalc firstUnit = units.isEmpty() ? null : units.get(0);
        MarginTier emptyTier = tierFor(BigDecimal.ZERO, in.settings().marginTiers());
        BigDecimal marginRate = overridden ? in.marginOverride()
                : firstUnit != null ? firstUnit.marginRate()
                : emptyTier == null ? BigDecimal.ZERO : emptyTier.ratePercent();
        BigDecimal marginTierMin = overridden ? null
                : firstUnit != null ? firstUnit.marginTierMin() : emptyTier == null ? null : emptyTier.minAmount();
        total = money(total);
        BigDecimal unitPrice = totalOrderQty.signum() > 0
                ? total.divide(totalOrderQty, LINE, RoundingMode.HALF_UP) : null;

        if (in.bomLines().isEmpty()) {
            warnings.add(I18n.t(locale, "quotation.warn.noBom"));
        }
        if (missing > 0) {
            warnings.add(I18n.t(locale, "quotation.warn.missingPrice", String.valueOf(missing)));
        }
        if (priceWarnings > 0) {
            warnings.add(I18n.t(locale, "quotation.warn.priceCheck", String.valueOf(priceWarnings)));
        }
        if (currencies.size() > 1) {
            warnings.add(I18n.t(locale, "quotation.warn.mixedCurrency", String.join(", ", currencies)));
        }
        if (!badSelections.isEmpty()) {
            warnings.add(I18n.t(locale, "quotation.warn.selectionIgnored", String.join(", ", badSelections)));
        }
        if (totalOrderQty.signum() <= 0) {
            warnings.add(I18n.t(locale, "quotation.warn.noQty"));
        }
        if (!manualLines.isEmpty()) {
            warnings.add(I18n.t(locale, "quotation.warn.manualPrice", String.join(", ", manualLines)));
        }

        return new Plan(in.order(), lines, product, packaging, productCost, packagingCost, materialCost,
                feeAmounts, money(costBeforeProfit), marginRate, marginTierMin, overridden, money(profit), total,
                unitPrice, currencies.size() == 1 ? currencies.iterator().next() : null, missing, priceWarnings,
                warnings, in.now(), units);
    }

    /**
     * The order lines, plus one for BOM lines whose order line was not passed
     * in (quantity from the BOM line), so no priced material is left out.
     */
    static List<OrderLine> linesWithItems(List<OrderLine> lines, List<Item> product, List<Item> packaging) {
        List<OrderLine> out = new ArrayList<>(lines);
        Set<String> known = new java.util.HashSet<>();
        for (OrderLine l : lines) {
            known.add(l.lineId());
        }
        for (List<Item> list : List.of(product, packaging)) {
            for (Item i : list) {
                if (known.add(i.lineId())) {
                    out.add(new OrderLine(i.lineId(), i.productCode(), null, null, i.orderQty(), null, null, null));
                }
            }
        }
        return out;
    }

    /** Sum of the unit total prices of one order line's items; null when none of them has a price. */
    static BigDecimal unitCost(List<Item> items, String lineId) {
        BigDecimal sum = null;
        for (Item i : items) {
            if (Objects.equals(i.lineId(), lineId) && i.unitTotalPrice() != null) {
                sum = (sum == null ? BigDecimal.ZERO : sum).add(i.unitTotalPrice());
            }
        }
        return sum;
    }

    private static BigDecimal lineAmount(List<Item> items, String lineId) {
        return items.stream().filter(i -> Objects.equals(i.lineId(), lineId)).map(Item::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static OrderLine withPrices(OrderLine l, BigDecimal materialCost, BigDecimal totalPrice,
            BigDecimal unitPrice, BigDecimal bomUnitCost) {
        return new OrderLine(l.lineId(), l.productCode(), l.productName(), l.spec(), l.qty(), l.unit(),
                l.productBomCode(), l.packagingBomCode(), materialCost, totalPrice, unitPrice, bomUnitCost);
    }

    /**
     * The calculation for one finished product: MATERIAL fees on the BOM cost
     * per unit, COST fees on that plus MATERIAL fees, the margin tier by the
     * cost before profit PER UNIT, PRICE fees on cost before profit plus
     * profit. Unit amounts are kept to 4 decimals; line total = unit price x
     * quantity.
     */
    static UnitCalc perUnit(OrderLine l, BigDecimal productUnit, BigDecimal packagingUnit, BigDecimal bomUnit,
            List<Fee> fees, List<MarginTier> tiers, BigDecimal marginOverride) {
        List<FeeAmount> amounts = new ArrayList<>();
        BigDecimal materialFees = BigDecimal.ZERO;
        for (Fee f : fees) {
            if (QuotationFeeBase.MATERIAL.name().equals(f.base())) {
                FeeAmount a = unitFee(f, bomUnit);
                amounts.add(a);
                materialFees = materialFees.add(a.amount());
            }
        }
        BigDecimal costBase = bomUnit.add(materialFees);
        BigDecimal costFees = BigDecimal.ZERO;
        for (Fee f : fees) {
            if (QuotationFeeBase.COST.name().equals(f.base())) {
                FeeAmount a = unitFee(f, costBase);
                amounts.add(a);
                costFees = costFees.add(a.amount());
            }
        }
        BigDecimal costBeforeProfit = costBase.add(costFees);
        MarginTier tier = tierFor(costBeforeProfit, tiers);
        boolean overridden = marginOverride != null;
        BigDecimal marginRate = overridden ? marginOverride : tier == null ? BigDecimal.ZERO : tier.ratePercent();
        BigDecimal profit = unitPercent(costBeforeProfit, marginRate);
        BigDecimal priceBase = costBeforeProfit.add(profit);
        BigDecimal priceFees = BigDecimal.ZERO;
        for (Fee f : fees) {
            if (QuotationFeeBase.PRICE.name().equals(f.base())) {
                FeeAmount a = unitFee(f, priceBase);
                amounts.add(a);
                priceFees = priceFees.add(a.amount());
            }
        }
        BigDecimal unitPrice = priceBase.add(priceFees).setScale(LINE, RoundingMode.HALF_UP);
        BigDecimal totalPrice = money(unitPrice.multiply(nz(l.qty())));
        return new UnitCalc(l.lineId(), l.productCode(), l.productName(), l.qty(), unitScale(productUnit),
                unitScale(packagingUnit), unitScale(bomUnit), amounts, unitScale(costBeforeProfit), marginRate,
                overridden || tier == null ? null : tier.minAmount(), overridden, profit, unitPrice, totalPrice);
    }

    private static FeeAmount unitFee(Fee f, BigDecimal base) {
        return new FeeAmount(f.code(), f.base(), f.ratePercent(), unitScale(base), unitPercent(base, f.ratePercent()));
    }

    private static BigDecimal unitPercent(BigDecimal amount, BigDecimal ratePercent) {
        return amount.multiply(nz(ratePercent)).divide(BigDecimal.valueOf(100), LINE, RoundingMode.HALF_UP);
    }

    private static BigDecimal unitScale(BigDecimal v) {
        return v.setScale(LINE, RoundingMode.HALF_UP);
    }

    /**
     * Quantity per finished product of every BOM line with a key: its own
     * quantity per parent times that of every parent up the tree (a parent
     * that is not in the list counts as 1; a loop stops the walk).
     */
    static Map<String, BigDecimal> qtyPerProduct(List<BomLineRow> rows) {
        Map<String, BomLineRow> byKey = new HashMap<>();
        for (BomLineRow r : rows) {
            if (r.lineKey() != null) {
                byKey.put(r.lineKey(), r);
            }
        }
        Map<String, BigDecimal> out = new HashMap<>();
        for (BomLineRow r : byKey.values()) {
            BigDecimal q = nz(r.qtyPerUnit());
            Set<String> seen = new java.util.HashSet<>();
            seen.add(r.lineKey());
            BomLineRow p = r.parentKey() == null ? null : byKey.get(r.parentKey());
            while (p != null && seen.add(p.lineKey())) {
                q = q.multiply(nz(p.qtyPerUnit()));
                p = p.parentKey() == null ? null : byKey.get(p.parentKey());
            }
            out.put(r.lineKey(), q);
        }
        return out;
    }

    /** PRODUCT or PACKAGING for one BOM line. */
    static String kindOf(BomLineRow row, List<String> packagingPrefixes) {
        if (row.bomKind() != null) {
            String k = row.bomKind().strip().toUpperCase(Locale.ROOT);
            if (KIND_PACKAGING.equals(k) || KIND_PRODUCT.equals(k)) {
                return k;
            }
        }
        String clas = row.materialClass();
        if (clas != null && packagingPrefixes != null) {
            for (String p : packagingPrefixes) {
                if (p != null && !p.isBlank() && clas.startsWith(p.strip())) {
                    return KIND_PACKAGING;
                }
            }
        }
        return KIND_PRODUCT;
    }

    /**
     * OK when low &lt; price / base &lt; high (same rule as the purchasing
     * Excel sheet: 0.8 and 1.2), HIGH / LOW outside, NO_BASE without a last
     * purchase price, NO_PRICE without a supplier price.
     */
    static String priceCheck(BigDecimal price, BigDecimal base, BigDecimal low, BigDecimal high) {
        if (price == null) {
            return "NO_PRICE";
        }
        if (base == null || base.signum() <= 0) {
            return "NO_BASE";
        }
        BigDecimal ratio = price.divide(base, 6, RoundingMode.HALF_UP);
        if (high != null && ratio.compareTo(high) >= 0) {
            return "HIGH";
        }
        if (low != null && ratio.compareTo(low) <= 0) {
            return "LOW";
        }
        return "OK";
    }

    /** Highest tier whose minimum the cost reaches; below all of them, the lowest tier. */
    static MarginTier tierFor(BigDecimal cost, List<MarginTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        List<MarginTier> sorted = tiers.stream()
                .sorted(Comparator.comparing(t -> nz(t.minAmount()))).toList();
        MarginTier chosen = sorted.get(0);
        for (MarginTier t : sorted) {
            if (cost.compareTo(nz(t.minAmount())) >= 0) {
                chosen = t;
            }
        }
        return chosen;
    }

    /** The material's last purchase price as an offer; null when it was never bought (or the supplier is unknown). */
    static SupplierPrice lastPurchaseOffer(BomLineRow row) {
        LastPurchase lp = row.lastPurchase();
        if (lp == null || lp.supplierCode() == null || row.basePrice() == null || row.basePrice().signum() <= 0) {
            return null;
        }
        return new SupplierPrice(SupplierPrice.LAST_PURCHASE_KEY, QuotationViews.PRICE_SOURCE_LAST_PURCHASE,
                lp.supplierCode(), lp.supplierName(), row.basePrice(), lp.taxRate(), lp.currency(), null, null,
                lp.date(), lp.poNo());
    }

    /** Valid purchase strategy prices per material, one per supplier (the lowest), cheapest first. */
    static Map<String, List<SupplierPrice>> suppliersByMaterial(List<PriceRow> rows) {
        Map<String, Map<String, SupplierPrice>> best = new LinkedHashMap<>();
        for (PriceRow r : rows) {
            if (r.materialCode() == null || r.supplierCode() == null || r.price() == null || r.price().signum() <= 0) {
                continue;
            }
            SupplierPrice p = SupplierPrice.strategy(r.supplierCode(), r.supplierName(), r.price(), r.taxRate(),
                    r.currency(), r.validFrom(), r.validTo());
            best.computeIfAbsent(r.materialCode(), k -> new LinkedHashMap<>())
                    .merge(r.supplierCode(), p, (a, b) -> b.price().compareTo(a.price()) < 0 ? b : a);
        }
        Map<String, List<SupplierPrice>> out = new HashMap<>();
        best.forEach((material, bySupplier) -> out.put(material, bySupplier.values().stream()
                .sorted(Comparator.comparing(SupplierPrice::price).thenComparing(SupplierPrice::supplierCode))
                .toList()));
        return out;
    }

    private static BigDecimal sum(List<Item> items) {
        return items.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
