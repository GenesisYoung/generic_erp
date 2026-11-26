package com.gsgd.generic_erp.service.purchase.legacy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Runs the configured quotation SELECTs ({@code erp.legacy.quotation.*})
 * against the old ERP. Same safety nets as the other legacy readers:
 * SELECT/WITH only, on a read-only pool.
 */
public class JdbcLegacyQuotationReader implements LegacyQuotationReader {

    /** SQL Server allows 2,100 parameters per statement; stay well below. */
    static final int IN_CHUNK = 1000;

    /** Order list: its maxRows is set to the batch limit on every call. */
    private final NamedParameterJdbcTemplate jdbc;
    /**
     * BOM lines and prices: never limited. Spring 7 treats every maxRows
     * other than -1 as a limit that its row mappers apply themselves, so 0
     * means "no row at all", not "no limit" as in JDBC.
     */
    private final NamedParameterJdbcTemplate unlimited;
    private final String orderQuery;
    private final String bomLineQuery;
    private final String priceQuery;

    public JdbcLegacyQuotationReader(JdbcTemplate jdbcTemplate, String orderQuery, String bomLineQuery,
            String priceQuery) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        JdbcTemplate all = new JdbcTemplate(jdbcTemplate.getDataSource());
        all.setQueryTimeout(jdbcTemplate.getQueryTimeout());
        all.setMaxRows(-1);
        this.unlimited = new NamedParameterJdbcTemplate(all);
        this.orderQuery = check(orderQuery, "erp.legacy.quotation.order-query");
        this.bomLineQuery = check(bomLineQuery, "erp.legacy.quotation.bom-line-query");
        this.priceQuery = check(priceQuery, "erp.legacy.quotation.price-query");
    }

    private static String check(String q, String property) {
        return q == null || q.isBlank() ? null : JdbcLegacyPurchaseOrderReader.requireSelect(q, property);
    }

    @Override
    public boolean supported() {
        return orderQuery != null && bomLineQuery != null && priceQuery != null;
    }

    @Override
    public List<OrderLineRow> orderLines(LocalDate since, int limit) {
        if (orderQuery == null) {
            return List.of();
        }
        jdbc.getJdbcTemplate().setMaxRows(limit);
        return jdbc.query(orderQuery, Map.of("since", Date.valueOf(since)), (rs, i) -> new OrderLineRow(
                str(rs, "order_no"), str(rs, "customer_code"), str(rs, "customer_name"),
                date(rs, "order_date"), date(rs, "delivery_date"), str(rs, "plan_no"), date(rs, "plan_date"),
                str(rs, "line_id"), str(rs, "product_code"), str(rs, "product_name"), str(rs, "spec"),
                num(rs, "qty"), str(rs, "unit"), blankToNull(str(rs, "product_bom_code")),
                blankToNull(str(rs, "packaging_bom_code")), optInt(rs, "plan_lines"),
                optInt(rs, "plan_lines_bought")));
    }

    @Override
    public List<BomLineRow> bomLines(String orderNo, Collection<String> lineIds) {
        if (bomLineQuery == null || lineIds == null || lineIds.isEmpty()) {
            return List.of();
        }
        return unlimited.query(bomLineQuery, bomParams(orderNo, lineIds), (rs, i) -> new BomLineRow(
                blankToNull(str(rs, "bom_kind")), str(rs, "bom_code"), str(rs, "line_id"), str(rs, "product_code"),
                num(rs, "order_qty"), integer(rs, "line_no"), integer(rs, "bom_level"), str(rs, "material_code"),
                str(rs, "material_name"), str(rs, "spec"), str(rs, "unit"), str(rs, "material_class"),
                num(rs, "qty_per_unit"), rs.getInt("leaf") != 0, num(rs, "base_price"),
                blankToNull(str(rs, "line_key")), blankToNull(str(rs, "parent_key")), lastPurchase(rs),
                hasColumn(rs, "processing") && rs.getInt("processing") != 0));
    }

    private static Map<String, Object> bomParams(String orderNo, Collection<String> lineIds) {
        return Map.of("orderNo", orderNo == null ? "" : orderNo, "lineIds", List.copyOf(new LinkedHashSet<>(lineIds)));
    }

    @Override
    public String diagnose(String orderNo, Collection<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            return "no order line ids";
        }
        List<String> counts = unlimited.query(DIAGNOSE_QUERY, bomParams(orderNo, lineIds),
                (rs, i) -> "orders with this no.=" + rs.getInt("orders") + ", order lines found=" + rs.getInt("lines")
                        + ", lines with BOMCODE=" + rs.getInt("lines_with_bomcode") + ", product BOM headers="
                        + rs.getInt("product_headers") + ", packaging BOM rows=" + rs.getInt("packaging_rows"));
        return counts.isEmpty() ? "the check query returned no row" : counts.get(0);
    }

    /** Counts each step of the BOM lookup; scalar subqueries only, nothing inside an aggregate. */
    static final String DIAGNOSE_QUERY = """
            SELECT
                (SELECT COUNT(*) FROM ORDCORD1 WITH (NOLOCK) WHERE CODE = :orderNo) AS orders,
                (SELECT COUNT(*) FROM ORDCORD2 WITH (NOLOCK) WHERE ERI IN (:lineIds)) AS lines,
                (SELECT COUNT(*) FROM ORDCORD2 WITH (NOLOCK)
                  WHERE ERI IN (:lineIds) AND ISNULL(BOMCODE, '') <> '') AS lines_with_bomcode,
                (SELECT COUNT(*) FROM ORDCORD2 cl WITH (NOLOCK)
                   JOIN ORDBOM1 b WITH (NOLOCK)
                     ON b.SRCERI = cl.ERI OR (ISNULL(cl.BOMCODE, '') <> '' AND b.CODE = cl.BOMCODE)
                  WHERE cl.ERI IN (:lineIds)) AS product_headers,
                (SELECT COUNT(*) FROM ORDCORD2 cl WITH (NOLOCK)
                   JOIN ORDBOM3 o3 WITH (NOLOCK) ON o3.CODE = cl.BOMCODE
                  WHERE cl.ERI IN (:lineIds)) AS packaging_rows
            """;

    @Override
    public List<PriceRow> prices(Collection<String> materialCodes) {
        if (priceQuery == null || materialCodes == null || materialCodes.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(materialCodes));
        List<PriceRow> out = new ArrayList<>();
        for (int from = 0; from < codes.size(); from += IN_CHUNK) {
            List<String> chunk = codes.subList(from, Math.min(codes.size(), from + IN_CHUNK));
            out.addAll(unlimited.query(priceQuery, Map.of("materialCodes", chunk), (rs, i) -> new PriceRow(
                    str(rs, "material_code"), str(rs, "supplier_code"), str(rs, "supplier_name"),
                    num(rs, "price"), num(rs, "tax_rate"), blankToNull(str(rs, "currency")),
                    date(rs, "valid_from"), date(rs, "valid_to"),
                    hasColumn(rs, "price_type") && "PROCESSING".equalsIgnoreCase(str(rs, "price_type")))));
        }
        return out;
    }

    private static String str(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        return s == null ? null : s.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }

    private static BigDecimal num(ResultSet rs, String col) throws SQLException {
        // The old ERP stores quantities and prices as float: go through the
        // decimal string so 0.1 stays 0.1 instead of 0.1000000000000000055.
        double d = rs.getDouble(col);
        return rs.wasNull() ? null : new BigDecimal(Double.toString(d));
    }

    private static Integer integer(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    /** The last purchase columns; null when the query leaves them out or the material was never bought. */
    private static LegacyQuotationReader.LastPurchase lastPurchase(ResultSet rs) throws SQLException {
        if (!hasColumn(rs, "last_supplier_code")) {
            return null;
        }
        String supplier = blankToNull(str(rs, "last_supplier_code"));
        if (supplier == null) {
            return null;
        }
        return new LegacyQuotationReader.LastPurchase(supplier,
                hasColumn(rs, "last_supplier_name") ? blankToNull(str(rs, "last_supplier_name")) : null,
                hasColumn(rs, "last_purchase_date") ? date(rs, "last_purchase_date") : null,
                hasColumn(rs, "last_po_no") ? blankToNull(str(rs, "last_po_no")) : null,
                hasColumn(rs, "last_tax_rate") ? num(rs, "last_tax_rate") : null,
                hasColumn(rs, "last_currency") ? blankToNull(str(rs, "last_currency")) : null);
    }

    private static boolean hasColumn(ResultSet rs, String col) {
        try {
            rs.findColumn(col);
            return true;
        } catch (SQLException missing) {
            return false;
        }
    }

    /** Like {@link #integer} but null when a custom query leaves the column out. */
    private static Integer optInt(ResultSet rs, String col) throws SQLException {
        try {
            rs.findColumn(col);
        } catch (SQLException missing) {
            return null;
        }
        return integer(rs, col);
    }
}
