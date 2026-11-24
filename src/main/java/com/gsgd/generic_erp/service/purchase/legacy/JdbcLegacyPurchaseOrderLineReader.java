package com.gsgd.generic_erp.service.purchase.legacy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Runs the configured line SELECT ({@code erp.legacy.purchase-order.line-query})
 * against the old ERP with the named parameter {@code :poNos} (a list of order
 * numbers). Order numbers are sent in chunks, because SQL Server accepts at
 * most 2,100 parameters per statement. Only SELECT statements are accepted.
 */
public class JdbcLegacyPurchaseOrderLineReader implements LegacyPurchaseOrderLineReader {

    static final int CHUNK = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final String query;

    public JdbcLegacyPurchaseOrderLineReader(JdbcTemplate jdbcTemplate, String query) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.query = JdbcLegacyPurchaseOrderReader.requireSelect(query, "erp.legacy.purchase-order.line-query");
    }

    @Override
    public List<LegacyPurchaseOrderLineRow> fetchLines(Collection<String> poNos) {
        List<String> all = poNos.stream().filter(n -> n != null && !n.isBlank()).distinct().toList();
        List<LegacyPurchaseOrderLineRow> rows = new ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) {
            List<String> chunk = all.subList(i, Math.min(i + CHUNK, all.size()));
            rows.addAll(jdbc.query(query, Map.of("poNos", chunk), (rs, n) -> new LegacyPurchaseOrderLineRow(
                    trim(rs.getString("po_no")),
                    integer(rs, "line_no"),
                    trim(rs.getString("material_code")),
                    trim(rs.getString("material_name")),
                    blankToNull(rs.getString("plan_type")),
                    blankToNull(rs.getString("plan_type_name")),
                    decimal(rs, "qty"),
                    decimal(rs, "received_qty"))));
        }
        return rows;
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Double decimal(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}
