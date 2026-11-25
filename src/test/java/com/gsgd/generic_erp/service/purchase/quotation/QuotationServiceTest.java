package com.gsgd.generic_erp.service.purchase.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderSummary;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.repository.purchase.QuotationRateRepository;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.BomLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.OrderLineRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyQuotationReader.PriceRow;

class QuotationServiceTest {

    final LegacyQuotationReader reader = mock(LegacyQuotationReader.class);
    final QuotationRateRepository repo = mock(QuotationRateRepository.class);

    @SuppressWarnings("unchecked")
    QuotationService service(LegacyQuotationReader r) {
        ObjectProvider<LegacyQuotationReader> rp = mock(ObjectProvider.class);
        when(rp.getIfAvailable()).thenReturn(r);
        ObjectProvider<LegacyErpProperties> pp = mock(ObjectProvider.class);
        when(pp.getIfAvailable()).thenReturn(new LegacyErpProperties());
        return new QuotationService(rp, pp, new QuotationSettingsService(repo));
    }

    static OrderLineRow line(String order, String lineId, String product, String productBom, String packBom,
            LocalDate planDate) {
        return new OrderLineRow(order, "C1", "Customer", LocalDate.of(2026, 11, 1), null, order, planDate, lineId,
                product, product + " name", null, BigDecimal.TEN, "PCS", productBom, packBom);
    }

    @Test
    void groupsLinesAndShowsBomStatus() {
        when(reader.supported()).thenReturn(true);
        when(reader.orderLines(any(), anyInt())).thenReturn(List.of(
                line("A-1", "L1", "P1", "B1", null, LocalDate.of(2026, 11, 20)),
                line("A-1", "L1", "P1", null, "K1", LocalDate.of(2026, 11, 21)), // same line, second plan line
                line("A-1", "L2", "P2", null, null, LocalDate.of(2026, 11, 20)),
                line("B-2", "L3", "P3", null, null, LocalDate.of(2026, 11, 22)),
                line("C-3", "L4", "P4", null, "K4", LocalDate.of(2026, 11, 19))));
        QuotationService s = service(reader);

        List<OrderSummary> all = s.orders("ALL", null, false);
        assertEquals(List.of("B-2", "A-1", "C-3"), all.stream().map(OrderSummary::orderNo).toList());
        OrderSummary a = all.get(1);
        assertEquals(2, a.lineCount());
        assertEquals("BOTH", a.bomStatus(), "BOM codes of a repeated line are merged");
        assertEquals(0, new BigDecimal("20").compareTo(a.totalQty()));

        assertEquals(List.of("B-2"), s.orders("WITHOUT", null, false).stream().map(OrderSummary::orderNo).toList());
        assertEquals(2, s.orders("WITH", null, false).size());
        assertEquals(List.of("C-3"), s.orders("PACKAGING_ONLY", null, false).stream().map(OrderSummary::orderNo)
                .toList());
        assertEquals(List.of("A-1"), s.orders(null, "p2 NAME", false).stream().map(OrderSummary::orderNo).toList());
        verify(reader, times(1)).orderLines(any(), anyInt());
    }

    @Test
    void buildsThePlanFromTheOldErp() {
        when(reader.supported()).thenReturn(true);
        when(reader.orderLines(any(), anyInt())).thenReturn(List.of(
                line("A-1", "L1", "P1", "B1", null, LocalDate.of(2026, 11, 20))));
        when(reader.bomLines("A-1")).thenReturn(List.of(new BomLineRow(null, "B1", "L1", "P1", BigDecimal.TEN, 1, 1,
                "M1", "Material", null, "PCS", "202", BigDecimal.ONE, true, null)));
        when(reader.prices(any())).thenReturn(List.of(new PriceRow("M1", "S1", "Supplier", new BigDecimal("100"),
                null, "RMB", null, null)));
        Plan p = service(reader).plan(" A-1 ", null);
        assertEquals(new BigDecimal("1000.00"), p.materialCost());
        assertEquals(1, p.lines().size());
    }

    @Test
    void unknownOrderAndOldErpOff() {
        when(reader.supported()).thenReturn(true);
        when(reader.orderLines(any(), anyInt())).thenReturn(List.of());
        QuotationException e = assertThrows(QuotationException.class, () -> service(reader).plan("Z-9", null));
        assertEquals(404, e.getStatus());
        assertEquals(503, assertThrows(QuotationException.class, () -> service(null).orders(null, null, false))
                .getStatus());
    }
}
