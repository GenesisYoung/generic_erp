package com.gsgd.generic_erp.service.purchase.legacy;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Runs the configured SELECT against the old ERP. Only statements that start
 * with SELECT or WITH are accepted, and the connection pool itself is
 * read-only (see {@link LegacyErpDataSourceConfig}), so this class cannot
 * change anything in the old ERP.
 */
public class JdbcLegacyPurchaseOrderReader implements LegacyPurchaseOrderReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final String query;

    public JdbcLegacyPurchaseOrderReader(JdbcTemplate jdbcTemplate, String query) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.query = requireSelect(query);
    }

    static String requireSelect(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalStateException("erp.legacy.purchase-order.query is not set");
        }
        String head = query.stripLeading().toUpperCase(Locale.ROOT);
        if (!(head.startsWith("SELECT") || head.startsWith("WITH"))) {
            throw new IllegalStateException("erp.legacy.purchase-order.query must be a SELECT statement");
        }
        return query;
    }

    @Override
    public List<LegacyPurchaseOrderRow> fetchChangedSince(LocalDateTime since, int limit) {
        jdbc.getJdbcTemplate().setMaxRows(limit);
        return jdbc.query(query, Map.of("since", Timestamp.valueOf(since)),
                (rs, i) -> new LegacyPurchaseOrderRow(
                        trim(rs.getString("po_no")),
                        trim(rs.getString("supplier_code")),
                        trim(rs.getString("supplier_name")),
                        trim(rs.getString("buyer_code")),
                        date(rs, "order_date"),
                        date(rs, "expected_date"),
                        trim(rs.getString("status")),
                        timestamp(rs, "updated_at")));
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }
}
