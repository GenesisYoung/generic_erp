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

    private final NamedParameterJdbcTemplate jdbc;
    private final String orderQuery;
    private final String bomLineQuery;
    private final String priceQuery;

    public JdbcLegacyQuotationReader(JdbcTemplate jdbcTemplate, String orderQuery, String bomLineQuery,
            String priceQuery) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
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
                blankToNull(str(rs, "packaging_bom_code"))));
    }

    @Override
    public List<BomLineRow> bomLines(String orderNo) {
        if (bomLineQuery == null || orderNo == null) {
            return List.of();
        }
        jdbc.getJdbcTemplate().setMaxRows(0);
        return jdbc.query(bomLineQuery, Map.of("orderNo", orderNo), (rs, i) -> new BomLineRow(
                blankToNull(str(rs, "bom_kind")), str(rs, "bom_code"), str(rs, "line_id"), str(rs, "product_code"),
                num(rs, "order_qty"), integer(rs, "line_no"), integer(rs, "bom_level"), str(rs, "material_code"),
                str(rs, "material_name"), str(rs, "spec"), str(rs, "unit"), str(rs, "material_class"),
                num(rs, "qty_per_unit"), rs.getInt("leaf") != 0, num(rs, "base_price"),
                blankToNull(str(rs, "line_key")), blankToNull(str(rs, "parent_key"))));
    }

    @Override
    public List<PriceRow> prices(Collection<String> materialCodes) {
        if (priceQuery == null || materialCodes == null || materialCodes.isEmpty()) {
            return List.of();
        }
        jdbc.getJdbcTemplate().setMaxRows(0);
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(materialCodes));
        List<PriceRow> out = new ArrayList<>();
        for (int from = 0; from < codes.size(); from += IN_CHUNK) {
            List<String> chunk = codes.subList(from, Math.min(codes.size(), from + IN_CHUNK));
            out.addAll(jdbc.query(priceQuery, Map.of("materialCodes", chunk), (rs, i) -> new PriceRow(
                    str(rs, "material_code"), str(rs, "supplier_code"), str(rs, "supplier_name"),
                    num(rs, "price"), num(rs, "tax_rate"), blankToNull(str(rs, "currency")),
                    date(rs, "valid_from"), date(rs, "valid_to"))));
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
}
