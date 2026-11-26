package com.gsgd.generic_erp.service.purchase.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JdbcLegacyQuotationReaderInlineTest {

    @Test
    void inlinesValuesAndLists() {
        String sql = "SELECT 1 WHERE a = :orderNo AND b IN (:lineIds) AND c = 'x:y'";
        assertEquals("SELECT 1 WHERE a = '26405-W1' AND b IN ('A1', 'B''2') AND c = 'x:y'",
                JdbcLegacyQuotationReader.inline(sql, Map.of("orderNo", "26405-W1", "lineIds", List.of("A1", "B'2"))));
    }

    @Test
    void usesUnicodeLiteralForNonAscii() {
        assertEquals("N'包装'", JdbcLegacyQuotationReader.literal("包装"));
    }

    @Test
    void refusesControlCharactersAndMissingValues() {
        assertThrows(IllegalArgumentException.class, () -> JdbcLegacyQuotationReader.literal("a\nb"));
        assertThrows(IllegalArgumentException.class,
                () -> JdbcLegacyQuotationReader.inline("SELECT :x", Map.of()));
    }
}
