package com.gsgd.generic_erp.service.purchase.legacy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.gsgd.generic_erp.enums.PlacementOrderType;

/**
 * Runs the configured purchase application / purchase plan SELECTs against
 * the old ERP. Same safety nets as {@link JdbcLegacyPurchaseOrderReader}:
 * SELECT/WITH only, on a read-only pool. A blank query switches that order
 * type off.
 */
public class JdbcLegacyPlacementSourceReader implements LegacyPlacementSourceReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<PlacementOrderType, String> queries = new EnumMap<>(PlacementOrderType.class);

    public JdbcLegacyPlacementSourceReader(JdbcTemplate jdbcTemplate, String sampleQuery, String productionQuery) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        if (sampleQuery != null && !sampleQuery.isBlank()) {
            queries.put(PlacementOrderType.SAMPLE, JdbcLegacyPurchaseOrderReader.requireSelect(sampleQuery,
                    "erp.legacy.placement-source.sample-query"));
        }
        if (productionQuery != null && !productionQuery.isBlank()) {
            queries.put(PlacementOrderType.PRODUCTION, JdbcLegacyPurchaseOrderReader.requireSelect(productionQuery,
                    "erp.legacy.placement-source.production-query"));
        }
    }

    @Override
    public boolean supports(PlacementOrderType type) {
        return queries.containsKey(type);
    }

    @Override
    public List<LegacyPlacementSourceRow> fetch(PlacementOrderType type, LocalDate since, int limit) {
        String sql = queries.get(type);
        if (sql == null) {
            return List.of();
        }
        jdbc.getJdbcTemplate().setMaxRows(limit);
        return jdbc.query(sql, Map.of("since", Date.valueOf(since)), (rs, i) -> {
            Timestamp d = rs.getTimestamp("source_date");
            return new LegacyPlacementSourceRow(
                    trim(rs.getString("order_no")),
                    trim(rs.getString("source_no")),
                    d == null ? null : d.toLocalDateTime(),
                    rs.getInt("confirmed") != 0);
        });
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }
}
