package com.gsgd.generic_erp.service.purchase.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.Fee;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.MarginTier;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.entity.purchase.QuotationRate;
import com.gsgd.generic_erp.repository.purchase.QuotationRateRepository;

class QuotationSettingsServiceTest {

    final QuotationRateRepository repo = mock(QuotationRateRepository.class);
    final QuotationSettingsService service = new QuotationSettingsService(repo);

    @Test
    void emptyTableMeansTheAgreedDefaults() {
        when(repo.findAllByOrderByKindAscSortOrderAscIdAsc()).thenReturn(List.of());
        Settings s = service.settings();
        assertEquals(6, s.fees().size());
        assertEquals(new BigDecimal("0.5"), s.fees().get(0).ratePercent());
        assertEquals("PRICE", s.fees().get(5).base(), "tax is on cost plus profit");
        assertEquals(3, s.marginTiers().size());
        assertEquals(new BigDecimal("10"), s.marginTiers().get(2).ratePercent());
    }

    @Test
    void savedRatesWin() {
        when(repo.findAllByOrderByKindAscSortOrderAscIdAsc()).thenReturn(List.of(
                QuotationRate.builder().kind("FEE").code("TAX").base("PRICE").ratePercent(new BigDecimal("13"))
                        .enabled(false).build(),
                QuotationRate.builder().kind("MARGIN").minAmount(BigDecimal.ZERO).ratePercent(new BigDecimal("20"))
                        .enabled(true).build()));
        Settings s = service.settings();
        Fee tax = s.fees().stream().filter(f -> f.code().equals("TAX")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("13"), tax.ratePercent());
        assertEquals(false, tax.enabled());
        assertEquals(1, s.marginTiers().size());
    }

    @Test
    void rejectsBadInput() {
        List<MarginTier> tiers = List.of(new MarginTier(BigDecimal.ZERO, BigDecimal.TEN));
        assertThrows(QuotationException.class, () -> service.update(new Settings(
                List.of(new Fee("FREIGHT", "MATERIAL", BigDecimal.ONE, true)), tiers, null, null), 1L));
        assertThrows(QuotationException.class, () -> service.update(new Settings(
                List.of(new Fee("TAX", "MATERIAL", new BigDecimal("101"), true)), tiers, null, null), 1L));
        assertThrows(QuotationException.class, () -> service.update(new Settings(List.of(), List.of(
                new MarginTier(BigDecimal.ONE, BigDecimal.TEN), new MarginTier(new BigDecimal("1.00"), BigDecimal.ONE)),
                null, null), 1L));
        assertThrows(QuotationException.class, () -> service.update(new Settings(List.of(), List.of(), null, null), 1L));
    }

    @Test
    void savesAndOverridesPerQuotation() {
        when(repo.findAllByOrderByKindAscSortOrderAscIdAsc()).thenReturn(List.of());
        service.update(new Settings(List.of(new Fee("TAX", "PRICE", new BigDecimal("6"), true)),
                List.of(new MarginTier(new BigDecimal("3000"), BigDecimal.TEN), new MarginTier(BigDecimal.ZERO,
                        new BigDecimal("15"))), null, null), 7L);
        verify(repo).deleteAllInBatch();
        verify(repo).saveAll(anyList());

        List<Fee> saved = service.settings().fees();
        List<Fee> out = service.withOverrides(saved, List.of(new Fee("LOADING", "PRICE", new BigDecimal("3"), false)));
        Fee loading = out.stream().filter(f -> f.code().equals("LOADING")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("3"), loading.ratePercent());
        assertEquals("MATERIAL", loading.base(), "the base is not changed per quotation");
        assertEquals(false, loading.enabled());
    }
}
