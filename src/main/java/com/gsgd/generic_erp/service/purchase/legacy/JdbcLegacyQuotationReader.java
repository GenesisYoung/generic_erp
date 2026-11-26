package com.gsgd.generic_erp.service.purchase.legacy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

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
                blankToNull(str(rs, "packaging_bom_code")), optInt(rs, "plan_lines"),
                optInt(rs, "plan_lines_bought")));
    }

    @Override
    public List<BomLineRow> bomLines(String orderNo, Collection<String> lineIds) {
        if (bomLineQuery == null || lineIds == null || lineIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> params = bomParams(orderNo, lineIds);
        return onCleanSession(t -> t.getJdbcTemplate().query(inline(bomLineQuery, params), (rs, i) -> new BomLineRow(
                blankToNull(str(rs, "bom_kind")), str(rs, "bom_code"), str(rs, "line_id"), str(rs, "product_code"),
                num(rs, "order_qty"), integer(rs, "line_no"), integer(rs, "bom_level"), str(rs, "material_code"),
                str(rs, "material_name"), str(rs, "spec"), str(rs, "unit"), str(rs, "material_class"),
                num(rs, "qty_per_unit"), rs.getInt("leaf") != 0, num(rs, "base_price"),
                blankToNull(str(rs, "line_key")), blankToNull(str(rs, "parent_key")))));
    }

    private static Map<String, Object> bomParams(String orderNo, Collection<String> lineIds) {
        return Map.of("orderNo", orderNo == null ? "" : orderNo, "lineIds", List.copyOf(new LinkedHashSet<>(lineIds)));
    }

    /**
     * Runs {@code work} on ONE pooled connection after switching off the two
     * session options that make SQL Server send result sets without rows:
     * FMTONLY (column information only) and ROWCOUNT (row limit, which the
     * driver uses for maxRows). Neither changes data. The driver is told the
     * limit is 0 again, so it stays in step with the session.
     */
    private <T> T onCleanSession(Function<NamedParameterJdbcTemplate, T> work) {
        return jdbc.getJdbcTemplate().execute((ConnectionCallback<T>) con -> {
            resetSession(con);
            JdbcTemplate single = new JdbcTemplate(new SingleConnectionDataSource(con, true));
            single.setQueryTimeout(jdbc.getJdbcTemplate().getQueryTimeout());
            single.setMaxRows(0);
            return work.apply(new NamedParameterJdbcTemplate(single));
        });
    }

    private static void resetSession(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.setMaxRows(0);
            st.execute("SET FMTONLY OFF; SET ROWCOUNT 0;");
        }
    }

    private static final Pattern PARAM = Pattern.compile("(?<![:\\w]):(\\w+)");

    /**
     * Puts the values into the SQL as quoted literals and runs it as a plain
     * statement. Through JDBC, parameterised statements on this old ERP came
     * back without rows (2026-11-26) while plain ones worked. Values are
     * codes read from the old ERP; quotes are doubled and control characters
     * refused, so a value can never end the literal.
     */
    static String inline(String sql, Map<String, Object> params) {
        Matcher m = PARAM.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException("No value for :" + name);
            }
            Object v = params.get(name);
            String lit;
            if (v instanceof Collection<?> c) {
                if (c.isEmpty()) {
                    throw new IllegalArgumentException("Empty list for :" + name);
                }
                List<String> parts = new ArrayList<>();
                for (Object o : c) {
                    parts.add(literal(String.valueOf(o)));
                }
                lit = String.join(", ", parts);
            } else {
                lit = literal(String.valueOf(v));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(lit));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String literal(String v) {
        for (int i = 0; i < v.length(); i++) {
            if (Character.isISOControl(v.charAt(i))) {
                throw new IllegalArgumentException("Control character in a value");
            }
        }
        boolean ascii = v.chars().allMatch(ch -> ch < 128);
        return (ascii ? "'" : "N'") + v.replace("'", "''") + "'";
    }

    /**
     * Everything the server sends back for {@code sql}: each result set with
     * its row count and first value, update counts, warnings and the error,
     * so nothing the driver might skip is lost.
     */
    static String rawResults(Connection con, String sql) {
        StringBuilder sb = new StringBuilder("[");
        try (Statement st = con.createStatement()) {
            boolean isRs = st.execute(sql);
            for (int guard = 0; guard < 20; guard++) {
                if (isRs) {
                    try (ResultSet rs = st.getResultSet()) {
                        int n = 0;
                        String first = null;
                        while (rs.next()) {
                            if (n == 0) {
                                first = rs.getString(1);
                            }
                            n++;
                        }
                        sb.append("resultSet(cols=").append(rs.getMetaData().getColumnCount()).append(", rows=")
                                .append(n).append(", first=").append(first).append(")");
                        appendWarnings(sb, rs.getWarnings());
                    }
                } else {
                    int uc = st.getUpdateCount();
                    if (uc == -1) {
                        break;
                    }
                    sb.append("updateCount=").append(uc);
                }
                sb.append(' ');
                isRs = st.getMoreResults();
            }
            appendWarnings(sb, st.getWarnings());
        } catch (SQLException e) {
            sb.append("ERROR ").append(e.getErrorCode()).append(" state ").append(e.getSQLState()).append(": ")
                    .append(e.getMessage());
        }
        return sb.append(']').toString();
    }

    private static void appendWarnings(StringBuilder sb, java.sql.SQLWarning w) {
        for (int i = 0; w != null && i < 10; i++, w = w.getNextWarning()) {
            sb.append(" warning ").append(w.getErrorCode()).append(": ").append(w.getMessage());
        }
    }

    private static int rowsOf(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = 0;
            while (rs.next()) {
                n++;
            }
            return n;
        }
    }

    @Override
    public String diagnose(String orderNo, Collection<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            return "no order line ids";
        }
        Map<String, Object> params = bomParams(orderNo, lineIds);
        return jdbc.getJdbcTemplate().execute((ConnectionCallback<String>) con -> {
            StringBuilder sb = new StringBuilder();
            sb.append("'SELECT 1' before reset returned ").append(rowsOf(con, "SELECT 1 AS one")).append(" row(s)");
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT @@SPID AS spid, @@SERVERNAME AS server_name, DB_NAME() AS db_name,"
                            + " SUSER_SNAME() AS login_name, @@OPTIONS AS options,"
                            + " CAST(SERVERPROPERTY('ProductVersion') AS varchar(30)) AS version")) {
                if (rs.next()) {
                    sb.append("; spid=").append(rs.getInt("spid")).append(", server=")
                            .append(rs.getString("server_name")).append(", database=").append(rs.getString("db_name"))
                            .append(", login=").append(rs.getString("login_name")).append(", options=")
                            .append(rs.getInt("options")).append(", version=").append(rs.getString("version"));
                } else {
                    sb.append("; the session query returned no row");
                }
            }
            resetSession(con);
            sb.append("; 'SELECT 1' after SET FMTONLY OFF / ROWCOUNT 0 returned ")
                    .append(rowsOf(con, "SELECT 1 AS one")).append(" row(s)");
            JdbcTemplate single = new JdbcTemplate(new SingleConnectionDataSource(con, true));
            single.setMaxRows(0);
            org.springframework.jdbc.core.RowMapper<String> countMapper = (rs, i) -> "orders with this no.=" + rs.getInt("orders") + ", order lines found="
                            + rs.getInt("lines") + ", lines with BOMCODE=" + rs.getInt("lines_with_bomcode")
                            + ", product BOM headers=" + rs.getInt("product_headers") + ", packaging BOM rows="
                            + rs.getInt("packaging_rows");
            List<String> prepared = new NamedParameterJdbcTemplate(single).query(DIAGNOSE_QUERY, params, countMapper);
            sb.append("; counts with parameters: ").append(prepared.isEmpty() ? "no row" : prepared.get(0));
            List<String> literal = single.query(inline(DIAGNOSE_QUERY, params), countMapper);
            sb.append("; counts with literal values: ").append(literal.isEmpty() ? "no row" : literal.get(0));
            sb.append("; raw results of the literal check: ").append(rawResults(con, inline(DIAGNOSE_QUERY, params)));
            sb.append("; raw results of one-table count: ").append(rawResults(con,
                    "SELECT COUNT(*) AS n FROM ORDCORD2 WITH (NOLOCK) WHERE ERI = "
                            + literal(lineIds.iterator().next())));
            sb.append("; raw results without NOLOCK: ").append(rawResults(con,
                    "SELECT COUNT(*) AS n FROM ORDCORD2 WHERE ERI = " + literal(lineIds.iterator().next())));
            return sb.toString();
        });
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
        return onCleanSession(t -> {
            List<PriceRow> out = new ArrayList<>();
            for (int from = 0; from < codes.size(); from += IN_CHUNK) {
                List<String> chunk = codes.subList(from, Math.min(codes.size(), from + IN_CHUNK));
                out.addAll(t.getJdbcTemplate().query(inline(priceQuery, Map.of("materialCodes", chunk)),
                        (rs, i) -> new PriceRow(
                        str(rs, "material_code"), str(rs, "supplier_code"), str(rs, "supplier_name"),
                        num(rs, "price"), num(rs, "tax_rate"), blankToNull(str(rs, "currency")),
                        date(rs, "valid_from"), date(rs, "valid_to"))));
            }
            return out;
        });
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
