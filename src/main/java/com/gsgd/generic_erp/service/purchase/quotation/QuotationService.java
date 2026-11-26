package com.gsgd.generic_erp.service.purchase.quotation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.CalculateRequest;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderLine;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderSummary;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.BomLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.OrderLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.PriceRow;

import lombok.RequiredArgsConstructor;

/**
 * Purchase quotations (报价方案): lists the orders that can be quoted (purchase
 * plan made, nothing bought yet) with their BOM status, and builds the
 * quotation plan of one order from its BOMs and the purchase strategy prices.
 * Nothing is written to the old ERP or stored here: a plan is calculated on
 * every request (the purchaser's supplier choices travel with the request).
 */
@Service
@RequiredArgsConstructor
public class QuotationService {

    private static final Logger log = LoggerFactory.getLogger(QuotationService.class);

    /** The order list is read again after this long (the old-ERP query is not cheap). */
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public static final String BOM_BOTH = "BOTH";
    public static final String BOM_PRODUCT_ONLY = "PRODUCT_ONLY";
    public static final String BOM_PACKAGING_ONLY = "PACKAGING_ONLY";
    public static final String BOM_NONE = "NONE";

    private final ObjectProvider<LegacyQuotationReader> readerProvider;
    private final ObjectProvider<LegacyErpProperties> legacyProperties;
    private final QuotationSettingsService settingsService;

    @Value("${erp.purchase.zone:Asia/Shanghai}")
    private String zone = "Asia/Shanghai";

    @Value("${erp.purchase.quotation.packaging-class-prefixes:3}")
    private List<String> packagingClassPrefixes = List.of("3");

    /** List plans that are partly bought too (false = only plans with nothing bought yet). */
    @Value("${erp.purchase.quotation.include-partly-bought:true}")
    boolean includePartlyBought = true;

    /** "all": every open sales order is listed; "unbought": only orders whose plan is not fully bought. */
    @Value("${erp.purchase.quotation.order-scope:all}")
    String orderScope = "all";

    Clock clock = Clock.systemUTC();

    private volatile Snapshot snapshot;

    private record Snapshot(Instant readAt, Map<String, List<OrderLineRow>> byOrder) {
    }

    /** True when the old ERP is connected and the three quotation queries are set. */
    public boolean available() {
        LegacyQuotationReader r = readerProvider.getIfAvailable();
        return r != null && r.supported();
    }

    /**
     * Orders that can be quoted, newest plan first.
     *
     * @param bom     ALL (or null), WITH (at least one BOM), WITHOUT (no BOM),
     *                or one of BOTH / PRODUCT_ONLY / PACKAGING_ONLY / NONE
     * @param keyword part of the order no., customer or product (ignoring case)
     */
    public List<OrderSummary> orders(String bom, String keyword, boolean refresh) {
        Map<String, List<OrderLineRow>> byOrder = load(refresh);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.strip().toLowerCase(Locale.ROOT);
        List<OrderSummary> out = new ArrayList<>();
        for (List<OrderLineRow> rows : byOrder.values()) {
            OrderSummary s = summary(rows);
            if (matchesBom(s, bom) && (kw == null || matchesKeyword(rows, kw))) {
                out.add(s);
            }
        }
        // Newest sales order first; orders without a plan sort after those with one on the same day.
        out.sort(Comparator.comparing(OrderSummary::orderDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OrderSummary::planDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OrderSummary::orderNo, Comparator.reverseOrder()));
        return out;
    }

    /** The quotation plan of one order, with the purchaser's changes. */
    public Plan plan(String orderNo, CalculateRequest request) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new QuotationException(400, "quotation.error.orderRequired");
        }
        LegacyQuotationReader reader = reader();
        List<OrderLineRow> rows = load(false).get(orderNo.strip());
        if (rows == null) {
            // Maybe it became eligible after the last read.
            rows = load(true).get(orderNo.strip());
        }
        if (rows == null) {
            throw new QuotationException(404, "quotation.error.orderNotFound", orderNo.strip());
        }
        List<BomLineRow> bomLines;
        List<PriceRow> prices;
        try {
            List<String> lineIds = rows.stream().map(OrderLineRow::lineId).filter(Objects::nonNull).toList();
            bomLines = reader.bomLines(orderNo.strip(), lineIds);
            long leaves = bomLines.stream().filter(BomLineRow::leaf).count();
            long withMaterial = bomLines.stream().filter(b -> b.leaf() && b.materialCode() != null).count();
            log.info("Quotation: order {} -> {} BOM rows from the old ERP, {} leaf rows, {} leaf rows with a"
                    + " material code (order lines {}, BOM codes {})", orderNo.strip(), bomLines.size(), leaves,
                    withMaterial, rows.size(),
                    rows.stream().map(r -> r.productBomCode() + "/" + r.packagingBomCode()).toList());
            if (bomLines.isEmpty()) {
                log.warn("Quotation: no BOM rows for order {} (line ids {}). Check: {}", orderNo.strip(), lineIds,
                        diagnose(reader, orderNo.strip(), lineIds));
            }
            Set<String> materials = new LinkedHashSet<>();
            for (BomLineRow b : bomLines) {
                if (b.leaf() && b.materialCode() != null) {
                    materials.add(b.materialCode());
                }
            }
            prices = reader.prices(materials);
        } catch (RuntimeException e) {
            log.warn("Quotation: reading the old ERP failed for order {}", orderNo, e);
            throw new QuotationException(502, "quotation.error.legacyRead");
        }
        CalculateRequest req = request == null ? new CalculateRequest(null, null, null, null) : request;
        checkManualPrices(req.manualPrices());
        if (req.marginRate() != null) {
            QuotationSettingsService.checkRate(req.marginRate());
        }
        Settings saved = settingsService.settings();
        Settings effective = new Settings(settingsService.withOverrides(saved.fees(), req.fees()),
                saved.marginTiers(), saved.priceCheckLow(), saved.priceCheckHigh());
        return QuotationCalculator.calculate(new QuotationCalculator.Input(summary(rows), lines(rows), bomLines,
                prices, effective, req.selections(), req.marginRate(), packagingClassPrefixes,
                LocalDateTime.now(clock.withZone(ZoneId.of(zone))), I18n.currentLocale(), req.manualPrices()));
    }

    /** A typed-in price must be a number of at most 6 decimals and below 100,000,000 (0 = none). */
    static void checkManualPrices(Map<String, BigDecimal> prices) {
        if (prices == null) {
            return;
        }
        for (BigDecimal p : prices.values()) {
            if (p != null && (p.signum() < 0 || p.scale() > 6 || p.compareTo(new BigDecimal("100000000")) >= 0)) {
                throw new QuotationException(400, "quotation.error.manualPrice");
            }
        }
    }

    private static String diagnose(LegacyQuotationReader reader, String orderNo, List<String> lineIds) {
        try {
            return reader.diagnose(orderNo, lineIds);
        } catch (RuntimeException e) {
            return "the check query failed: " + e.getMessage();
        }
    }

    private LegacyQuotationReader reader() {
        LegacyQuotationReader r = readerProvider.getIfAvailable();
        if (r == null || !r.supported()) {
            throw new QuotationException(503, "quotation.error.legacyOff");
        }
        return r;
    }

    private Map<String, List<OrderLineRow>> load(boolean refresh) {
        Snapshot s = snapshot;
        Instant now = clock.instant();
        if (!refresh && s != null && Duration.between(s.readAt(), now).compareTo(CACHE_TTL) < 0) {
            return s.byOrder();
        }
        LegacyQuotationReader reader = reader();
        LegacyErpProperties props = legacyProperties.getIfAvailable();
        int lookback = props == null ? 90 : props.getQuotation().getLookbackDays();
        int limit = props == null ? 5000 : props.getQuotation().getBatchLimit();
        LocalDate since = LocalDate.now(clock.withZone(ZoneId.of(zone))).minusDays(lookback);
        List<OrderLineRow> rows;
        try {
            rows = reader.orderLines(since, limit);
        } catch (RuntimeException e) {
            log.warn("Quotation: reading eligible orders from the old ERP failed", e);
            throw new QuotationException(502, "quotation.error.legacyRead");
        }
        Map<String, List<OrderLineRow>> byOrder = group(rows.stream().filter(this::eligible).toList());
        snapshot = new Snapshot(now, byOrder);
        return byOrder;
    }

    /** Rows per order no., each order line once (a line can come from two plan lines). */
    static Map<String, List<OrderLineRow>> group(List<OrderLineRow> rows) {
        Map<String, Map<String, OrderLineRow>> tmp = new LinkedHashMap<>();
        for (OrderLineRow r : rows) {
            if (r.orderNo() == null) {
                continue;
            }
            String key = r.lineId() != null ? r.lineId() : r.productCode() + "|" + tmp.size();
            tmp.computeIfAbsent(r.orderNo(), k -> new LinkedHashMap<>()).merge(key, r, QuotationService::merge);
        }
        Map<String, List<OrderLineRow>> out = new LinkedHashMap<>();
        tmp.forEach((k, v) -> out.put(k, List.copyOf(v.values())));
        return out;
    }

    /**
     * With order-scope "all", every row. Otherwise: has a plan that is not fully bought; with
     * includePartlyBought off, nothing bought at all.
     */
    boolean eligible(OrderLineRow r) {
        if (!"unbought".equalsIgnoreCase(orderScope)) {
            return true;
        }
        if (r.planLines() != null && r.planLines() == 0) {
            return false;
        }
        if (r.planLines() == null || r.planLinesBought() == null) {
            return true;
        }
        int bought = r.planLinesBought();
        return includePartlyBought ? bought < r.planLines() : bought == 0;
    }

    /** Keeps the first row of an order line but fills BOM codes found on a duplicate. */
    private static OrderLineRow merge(OrderLineRow a, OrderLineRow b) {
        return new OrderLineRow(a.orderNo(), a.customerCode(), a.customerName(), a.orderDate(), a.deliveryDate(),
                a.planNo() != null ? a.planNo() : b.planNo(),
                a.planDate() == null || (b.planDate() != null && b.planDate().isAfter(a.planDate()))
                        ? b.planDate() : a.planDate(),
                a.lineId(), a.productCode(), a.productName(), a.spec(), a.qty(), a.unit(),
                a.productBomCode() != null ? a.productBomCode() : b.productBomCode(),
                a.packagingBomCode() != null ? a.packagingBomCode() : b.packagingBomCode(),
                a.planLines() != null ? a.planLines() : b.planLines(),
                a.planLinesBought() != null ? a.planLinesBought() : b.planLinesBought());
    }

    static OrderSummary summary(List<OrderLineRow> rows) {
        OrderLineRow first = rows.get(0);
        boolean product = rows.stream().anyMatch(r -> r.productBomCode() != null);
        boolean packaging = rows.stream().anyMatch(r -> r.packagingBomCode() != null);
        BigDecimal qty = rows.stream().map(r -> r.qty() == null ? BigDecimal.ZERO : r.qty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate planDate = rows.stream().map(OrderLineRow::planDate).filter(d -> d != null)
                .max(Comparator.naturalOrder()).orElse(null);
        String planNo = rows.stream().map(OrderLineRow::planNo).filter(p -> p != null).findFirst().orElse(null);
        // Plan line counts per plan (repeated on every row of that plan), added up over the plans.
        Map<String, int[]> perPlan = new LinkedHashMap<>();
        for (OrderLineRow r : rows) {
            if (r.planLines() != null) {
                perPlan.putIfAbsent(r.planNo() == null ? "" : r.planNo(),
                        new int[] {r.planLines(), r.planLinesBought() == null ? 0 : r.planLinesBought()});
            }
        }
        int planLines = perPlan.values().stream().mapToInt(a -> a[0]).sum();
        int planLinesBought = perPlan.values().stream().mapToInt(a -> a[1]).sum();
        String status = product && packaging ? BOM_BOTH : product ? BOM_PRODUCT_ONLY
                : packaging ? BOM_PACKAGING_ONLY : BOM_NONE;
        return new OrderSummary(first.orderNo(), first.customerCode(), first.customerName(), first.orderDate(),
                first.deliveryDate(), planNo, planDate, rows.size(), qty, product, packaging, status, planLines,
                planLinesBought);
    }

    static List<OrderLine> lines(List<OrderLineRow> rows) {
        return rows.stream().map(r -> new OrderLine(r.lineId(), r.productCode(), r.productName(), r.spec(),
                r.qty(), r.unit(), r.productBomCode(), r.packagingBomCode())).toList();
    }

    static boolean matchesBom(OrderSummary s, String bom) {
        if (bom == null || bom.isBlank() || "ALL".equalsIgnoreCase(bom)) {
            return true;
        }
        String b = bom.toUpperCase(Locale.ROOT);
        return switch (b) {
            case "WITH" -> !BOM_NONE.equals(s.bomStatus());
            case "WITHOUT" -> BOM_NONE.equals(s.bomStatus());
            default -> b.equals(s.bomStatus());
        };
    }

    private static boolean matchesKeyword(List<OrderLineRow> rows, String kw) {
        for (OrderLineRow r : rows) {
            for (String s : new String[] { r.orderNo(), r.customerCode(), r.customerName(), r.productCode(),
                    r.productName(), r.planNo() }) {
                if (s != null && s.toLowerCase(Locale.ROOT).contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }
}
