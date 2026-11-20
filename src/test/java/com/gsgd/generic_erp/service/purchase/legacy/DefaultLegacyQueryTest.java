package com.gsgd.generic_erp.service.purchase.legacy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The default old-ERP query in application.yml must be a SELECT that uses the
 * :since parameter and returns every column the reader needs.
 */
class DefaultLegacyQueryTest {

    @Test
    void defaultQueryReturnsTheColumnsTheReaderNeeds() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Object raw = sources.get(0).getProperty("erp.legacy.purchase-order.query");
        String query = String.valueOf(raw);

        assertDoesNotThrow(() -> JdbcLegacyPurchaseOrderReader.requireSelect(query));
        assertTrue(query.contains(":since"));
        for (String alias : List.of("po_no", "supplier_code", "supplier_name", "buyer_code", "order_date",
                "expected_date", "status", "updated_at")) {
            assertTrue(query.contains("AS " + alias), "missing alias " + alias);
        }
    }
}
