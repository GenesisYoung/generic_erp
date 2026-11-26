package com.gsgd.generic_erp.service.purchase.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * BOM lines and prices must come back in full, whatever limit the order list
 * used before. Spring 7 row mappers stop at maxRows unless it is -1, so a
 * maxRows of 0 returned no BOM row at all (order 26405-W1, 2026-11-26).
 */
class JdbcLegacyQuotationReaderRowsTest {

    private static final String BOM = """
            SELECT 'PRODUCT' AS bom_kind, 'B1' AS bom_code, line_id, 'P1' AS product_code, 1 AS order_qty,
                   n AS line_no, 1 AS bom_level, 'M' || n AS material_code, 'name' AS material_name,
                   '' AS spec, 'PCS' AS unit, 'C' AS material_class, 2 AS qty_per_unit, 1 AS leaf,
                   1.5 AS base_price, 'P' || n AS line_key, NULL AS parent_key,
                   CASE WHEN n = 1 THEN 'S7' END AS last_supplier_code, 'S7 Ltd' AS last_supplier_name,
                   DATE '2026-10-03' AS last_purchase_date, 'P2611' AS last_po_no, 13 AS last_tax_rate,
                   'RMB' AS last_currency
            FROM t WHERE line_id IN (:lineIds) ORDER BY n
            """;
    private static final String PRICE = """
            SELECT 'M' || n AS material_code, 'S1' AS supplier_code, 'Supplier' AS supplier_name, 1.2 AS price,
                   13 AS tax_rate, 'RMB' AS currency, NULL AS valid_from, NULL AS valid_to
            FROM t WHERE 'M' || n IN (:materialCodes)
            """;
    private static final String ORDERS = """
            SELECT 'O1' AS order_no, NULL AS customer_code, NULL AS customer_name, NULL AS order_date,
                   NULL AS delivery_date, NULL AS plan_no, NULL AS plan_date, line_id, NULL AS product_code,
                   NULL AS product_name, NULL AS spec, 1 AS qty, NULL AS unit, NULL AS product_bom_code,
                   NULL AS packaging_bom_code
            FROM t WHERE :since IS NOT NULL
            """;

    @Test
    void bomLinesAndPricesAreNotLimited() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:quotation_rows;DB_CLOSE_DELAY=-1");
        JdbcTemplate t = new JdbcTemplate(ds);
        t.execute("CREATE TABLE IF NOT EXISTS t (n INT, line_id VARCHAR(20))");
        t.execute("DELETE FROM t");
        for (int i = 1; i <= 5; i++) {
            t.update("INSERT INTO t VALUES (?, ?)", i, i <= 3 ? "L1" : "L2");
        }
        JdbcLegacyQuotationReader reader = new JdbcLegacyQuotationReader(t, ORDERS, BOM, PRICE);

        assertEquals(1, reader.orderLines(LocalDate.of(2026, 1, 1), 1).size());
        List<LegacyQuotationReader.BomLineRow> bom = reader.bomLines("O1", List.of("L1", "L2"));
        assertEquals(5, bom.size());
        assertEquals(new LegacyQuotationReader.LastPurchase("S7", "S7 Ltd", LocalDate.of(2026, 10, 3), "P2611",
                new java.math.BigDecimal("13.0"), "RMB"), bom.get(0).lastPurchase());
        assertEquals(null, bom.get(1).lastPurchase(), "never bought: no supplier");
        assertEquals(5, reader.prices(bom.stream().map(LegacyQuotationReader.BomLineRow::materialCode).toList())
                .size());

        t.setMaxRows(0);
        assertEquals(5, new JdbcLegacyQuotationReader(t, ORDERS, BOM, PRICE).bomLines("O1", List.of("L1", "L2"))
                .size());
    }
}
