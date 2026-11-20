package com.gsgd.generic_erp.service.purchase.legacy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JdbcLegacyPurchaseOrderReaderTest {

    @Test
    void acceptsOnlySelectStatements() {
        assertDoesNotThrow(() -> JdbcLegacyPurchaseOrderReader.requireSelect("  select po_no from t where u > :since"));
        assertDoesNotThrow(() -> JdbcLegacyPurchaseOrderReader.requireSelect("WITH x AS (SELECT 1) SELECT * FROM x"));
        assertThrows(IllegalStateException.class, () -> JdbcLegacyPurchaseOrderReader.requireSelect("DELETE FROM po"));
        assertThrows(IllegalStateException.class, () -> JdbcLegacyPurchaseOrderReader.requireSelect(" "));
        assertThrows(IllegalStateException.class, () -> JdbcLegacyPurchaseOrderReader.requireSelect(null));
    }
}
