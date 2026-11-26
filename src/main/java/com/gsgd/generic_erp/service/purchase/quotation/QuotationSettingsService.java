package com.gsgd.generic_erp.service.purchase.quotation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.Fee;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.MarginTier;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.entity.purchase.QuotationRate;
import com.gsgd.generic_erp.enums.QuotationFeeBase;
import com.gsgd.generic_erp.enums.QuotationFeeCode;
import com.gsgd.generic_erp.repository.purchase.QuotationRateRepository;

import lombok.RequiredArgsConstructor;

/**
 * Fee rates and profit margin tiers of purchase quotations
 * (quotation_rate_tb). Until someone saves the settings page, the defaults
 * apply: the fees of {@link QuotationFeeCode} and the margin tiers decided on
 * 2026-11-25 (order total: 3,000 or more = 10%, below = 15%).
 */
@Service
@RequiredArgsConstructor
public class QuotationSettingsService {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Default margin tiers: [minimum cost, percent]. */
    static final List<MarginTier> DEFAULT_TIERS = List.of(
            new MarginTier(BigDecimal.ZERO, new BigDecimal("15")),
            new MarginTier(new BigDecimal("1000"), new BigDecimal("15")),
            new MarginTier(new BigDecimal("3000"), new BigDecimal("10")));

    private final QuotationRateRepository repository;

    @Value("${erp.purchase.quotation.price-check-low:0.8}")
    private BigDecimal priceCheckLow = new BigDecimal("0.8");

    @Value("${erp.purchase.quotation.price-check-high:1.2}")
    private BigDecimal priceCheckHigh = new BigDecimal("1.2");

    @Transactional(readOnly = true)
    public Settings settings() {
        List<QuotationRate> rows = repository.findAllByOrderByKindAscSortOrderAscIdAsc();
        Map<String, QuotationRate> saved = rows.stream()
                .filter(r -> QuotationRate.KIND_FEE.equals(r.getKind()) && r.getCode() != null)
                .collect(Collectors.toMap(QuotationRate::getCode, Function.identity(), (a, b) -> a));
        List<Fee> fees = new ArrayList<>();
        for (QuotationFeeCode code : QuotationFeeCode.values()) {
            QuotationRate r = saved.get(code.name());
            fees.add(r == null
                    ? new Fee(code.name(), code.defaultBase().name(), code.defaultRate(), true)
                    : new Fee(code.name(), r.getBase(), r.getRatePercent(), r.isEnabled()));
        }
        List<MarginTier> tiers = rows.stream().filter(r -> QuotationRate.KIND_MARGIN.equals(r.getKind()))
                .map(r -> new MarginTier(r.getMinAmount(), r.getRatePercent())).toList();
        return new Settings(fees, tiers.isEmpty() ? DEFAULT_TIERS : tiers, priceCheckLow, priceCheckHigh);
    }

    /** Replaces every rate with the given ones (the band comes from configuration). */
    @Transactional
    public Settings update(Settings body, Long userId) {
        List<Fee> fees = validateFees(body == null ? null : body.fees());
        List<MarginTier> tiers = validateTiers(body == null ? null : body.marginTiers());
        LocalDateTime now = LocalDateTime.now();
        repository.deleteAllInBatch();
        List<QuotationRate> rows = new ArrayList<>();
        int i = 0;
        for (Fee f : fees) {
            rows.add(QuotationRate.builder().kind(QuotationRate.KIND_FEE).code(f.code()).base(f.base())
                    .ratePercent(f.ratePercent()).sortOrder(i++).enabled(f.enabled()).updatedAt(now)
                    .updatedBy(userId).build());
        }
        i = 0;
        for (MarginTier t : tiers) {
            rows.add(QuotationRate.builder().kind(QuotationRate.KIND_MARGIN).ratePercent(t.ratePercent())
                    .minAmount(t.minAmount()).sortOrder(i++).enabled(true).updatedAt(now).updatedBy(userId)
                    .build());
        }
        repository.saveAll(rows);
        return settings();
    }

    /**
     * The saved fees with this quotation's overrides (rate / enabled by code);
     * the base cannot be changed per quotation.
     */
    public List<Fee> withOverrides(List<Fee> saved, List<Fee> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return saved;
        }
        Map<String, Fee> byCode = overrides.stream().filter(f -> f != null && f.code() != null)
                .collect(Collectors.toMap(Fee::code, Function.identity(), (a, b) -> b));
        List<Fee> out = new ArrayList<>();
        for (Fee f : saved) {
            Fee o = byCode.get(f.code());
            if (o == null) {
                out.add(f);
            } else {
                checkRate(o.ratePercent());
                out.add(new Fee(f.code(), f.base(), o.ratePercent(), o.enabled()));
            }
        }
        return out;
    }

    static List<Fee> validateFees(List<Fee> fees) {
        if (fees == null) {
            throw new QuotationException(400, "quotation.error.feesRequired");
        }
        Set<String> codes = Arrays.stream(QuotationFeeCode.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> bases = Arrays.stream(QuotationFeeBase.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (Fee f : fees) {
            if (f == null || f.code() == null || !codes.contains(f.code()) || !seen.add(f.code())) {
                throw new QuotationException(400, "quotation.error.feeUnknown");
            }
            if (f.base() == null || !bases.contains(f.base())) {
                throw new QuotationException(400, "quotation.error.feeBase");
            }
            checkRate(f.ratePercent());
        }
        return fees;
    }

    static List<MarginTier> validateTiers(List<MarginTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new QuotationException(400, "quotation.error.tierRequired");
        }
        Set<BigDecimal> mins = new HashSet<>();
        for (MarginTier t : tiers) {
            if (t == null || t.minAmount() == null || t.minAmount().signum() < 0) {
                throw new QuotationException(400, "quotation.error.tierMin");
            }
            if (!mins.add(t.minAmount().stripTrailingZeros())) {
                throw new QuotationException(400, "quotation.error.tierDuplicate");
            }
            checkRate(t.ratePercent());
        }
        return tiers.stream().sorted((a, b) -> a.minAmount().compareTo(b.minAmount())).toList();
    }

    static void checkRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(HUNDRED) > 0) {
            throw new QuotationException(400, "quotation.error.rateRange");
        }
    }
}
