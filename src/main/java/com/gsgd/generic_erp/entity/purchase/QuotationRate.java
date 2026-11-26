package com.gsgd.generic_erp.entity.purchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One configurable rate of the purchase quotation (报价方案费率). Two kinds:
 * <ul>
 * <li>{@code FEE}: an additional fee ({@code code} = a
 * {@link com.gsgd.generic_erp.enums.QuotationFeeCode}), a percentage of
 * {@code base}.</li>
 * <li>{@code MARGIN}: a profit margin tier. The tier with the highest
 * {@code minAmount} that the cost (materials plus fees, before profit)
 * reaches applies.</li>
 * </ul>
 * Rates are percentages: 2.5 means 2.5%. Maps to table: quotation_rate_tb.
 */
@Entity
@Table(name = "quotation_rate_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class QuotationRate {

    public static final String KIND_FEE = "FEE";
    public static final String KIND_MARGIN = "MARGIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** FEE or MARGIN. */
    @Column(name = "kind", length = 10, nullable = false)
    private String kind;

    /** Fee code (FEE only). */
    @Column(name = "code", length = 40)
    private String code;

    /** MATERIAL, COST or PRICE (FEE only). */
    @Column(name = "base", length = 10)
    private String base;

    /** Percent, e.g. 2.5 = 2.5%. */
    @Column(name = "rate_percent", precision = 9, scale = 4, nullable = false)
    private BigDecimal ratePercent;

    /** Lower bound of the cost for this margin tier (MARGIN only). */
    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
