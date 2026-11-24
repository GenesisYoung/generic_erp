package com.gsgd.generic_erp.service.purchase.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** The line reader against H2: list parameter, chunking, blank plan types and NULL numbers. */
class JdbcLegacyPurchaseOrderLineReaderTest {

    static final String QUERY = """
            SELECT po_no, line_no, material_code, material_name, plan_type, plan_type_name, qty, received_qty
            FROM lines WHERE po_no IN (:poNos) ORDER BY po_no, line_no""";

    @Test
    void readsLinesOfTheGivenOrdersInChunks() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            jdbc.execute("""
                    CREATE TABLE lines (po_no VARCHAR(20), line_no INT, material_code VARCHAR(20),
                      material_name VARCHAR(50), plan_type VARCHAR(10), plan_type_name VARCHAR(20),
                      qty DOUBLE, received_qty DOUBLE)""");
            jdbc.update("INSERT INTO lines VALUES ('PO-1', 1, ' M1 ', 'Bolt', 'HW', '五金', 10, 2)");
            jdbc.update("INSERT INTO lines VALUES ('PO-1', 2, 'M2', 'Box', '  ', NULL, NULL, NULL)");
            jdbc.update("INSERT INTO lines VALUES ('PO-9', 1, 'M3', 'Other', 'PK', NULL, 1, 0)");
            var reader = new JdbcLegacyPurchaseOrderLineReader(jdbc, QUERY);

            // 1,200 order numbers -> 3 statements; only PO-1 matches.
            List<String> nos = IntStream.range(0, 1200).mapToObj(i -> "X" + i).collect(
                    java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            nos.add(700, "PO-1");
            List<LegacyPurchaseOrderLineRow> rows = reader.fetchLines(nos);

            assertEquals(2, rows.size());
            assertEquals(new LegacyPurchaseOrderLineRow("PO-1", 1, "M1", "Bolt", "HW", "五金", 10.0, 2.0), rows.get(0));
            assertNull(rows.get(1).planType());
            assertNull(rows.get(1).qty());
        } finally {
            db.shutdown();
        }
    }

    @Test
    void refusesAnythingButSelect() {
        assertThrows(IllegalStateException.class,
                () -> new JdbcLegacyPurchaseOrderLineReader(new JdbcTemplate(), "UPDATE lines SET qty = 0"));
    }
}
