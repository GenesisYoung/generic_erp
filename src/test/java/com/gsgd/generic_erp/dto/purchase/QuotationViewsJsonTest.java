package com.gsgd.generic_erp.dto.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.CalculateRequest;

import tools.jackson.databind.json.JsonMapper;

/** The request still reads with and without the manual prices (records with a second constructor). */
class QuotationViewsJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsManualPrices() {
        CalculateRequest r = json.readValue(
                "{\"selections\":{\"A\":\"LAST_PURCHASE\"},\"fees\":null,\"marginRate\":12,"
                        + "\"manualPrices\":{\"X\":0.75}}",
                CalculateRequest.class);
        assertEquals("LAST_PURCHASE", r.selections().get("A"));
        assertEquals(0, new BigDecimal("0.75").compareTo(r.manualPrices().get("X")));
        assertEquals(0, new BigDecimal("12").compareTo(r.marginRate()));
    }

    @Test
    void readsAnOlderRequestWithoutThem() {
        CalculateRequest r = json.readValue("{\"selections\":{},\"fees\":null,\"marginRate\":null}",
                CalculateRequest.class);
        assertNull(r.manualPrices());
    }
}
