package com.gsgd.generic_erp.service.purchase.legacy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Runs the configured customer order SELECT against the old ERP. Same safety
 * nets as {@link JdbcLegacyPurchaseOrderReader}: SELECT/WITH only, on a
 * read-only pool.
 */
public class JdbcLegacyCustomerOrderReader implements LegacyCustomerOrderReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final String query;
    private final String poLinkQuery;

    public JdbcLegacyCustomerOrderReader(JdbcTemplate jdbcTemplate, String query, String poLinkQuery) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.query = JdbcLegacyPurchaseOrderReader.requireSelect(query, "erp.legacy.customer-order.query");
        this.poLinkQuery = JdbcLegacyPurchaseOrderReader.requireSelect(poLinkQuery,
                "erp.legacy.customer-order.po-link-query");
    }

    @Override
    public List<LegacyPoLinkRow> fetchPoLinksSince(LocalDate since, int limit) {
        jdbc.getJdbcTemplate().setMaxRows(limit);
        return jdbc.query(poLinkQuery, Map.of("since", Date.valueOf(since)),
                (rs, i) -> new LegacyPoLinkRow(
                        trim(rs.getString("customer_order_id")),
                        trim(rs.getString("customer_order_no")),
                        trim(rs.getString("customer_line_id")),
                        trim(rs.getString("po_no")),
                        date(rs, "po_date")));
    }

    @Override
    public List<LegacyCustomerOrderRow> fetchDatedSince(LocalDate since, int limit) {
        jdbc.getJdbcTemplate().setMaxRows(limit);
        return jdbc.query(query, Map.of("since", Date.valueOf(since)),
                (rs, i) -> new LegacyCustomerOrderRow(
                        trim(rs.getString("legacy_id")),
                        trim(rs.getString("order_no")),
                        trim(rs.getString("customer_code")),
                        trim(rs.getString("customer_name")),
                        date(rs, "order_date"),
                        date(rs, "delivery_date"),
                        trim(rs.getString("sales_code")),
                        integer(rs, "line_count"),
                        decimal(rs, "total_qty"),
                        rs.getInt("legacy_audited") != 0,
                        rs.getInt("closed") != 0));
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal v = rs.getBigDecimal(column);
        return v == null ? null : v.stripTrailingZeros();
    }
}
