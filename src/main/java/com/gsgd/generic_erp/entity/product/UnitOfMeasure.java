package com.gsgd.generic_erp.entity.product;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Unit-of-measure definition mapped to {@code uom_tb}.
 *
 * <p>Ordinary physical units in the same dimension are converted through
 * {@code factorToBase}. Count-unit packaging ratios are product-specific and
 * belong in {@link ProductUnitOfMeasure}; temperature requires more than a
 * simple multiplier.</p>
 */
@Entity
@Table(name = "uom_tb", indexes = {
        @Index(name = "idx_uom_iso_code", columnList = "iso_code", unique = true),
        @Index(name = "idx_uom_unit_name", columnList = "unit_name", unique = true),
        @Index(name = "idx_uom_dimension", columnList = "dimension")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UnitOfMeasure extends ProductAuditableEntity {

    /** Unit-of-measure primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uom_id")
    private Long uomId;

    /** Unit code, for example KGM, EA, or BOX. */
    @Column(name = "iso_code", length = 8, nullable = false, unique = true)
    private String isoCode;

    /** Full unit name, for example Kilogram. */
    @Column(name = "unit_name", length = 32, nullable = false, unique = true)
    private String unitName;

    /** Display symbol, for example kg. */
    @Column(name = "symbol", length = 8)
    private String symbol;

    /** Physical dimension to which the unit belongs. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false)
    private UomDimension dimension;

    /** Whether this is the base unit; exactly one base unit is allowed per dimension. */
    @Column(name = "is_base_for_dimension", nullable = false)
    @lombok.Builder.Default
    private Boolean baseForDimension = false;

    /** Positive multiplier that converts this unit to its dimension's base unit. */
    @Column(name = "factor_to_base", precision = 24, scale = 10, nullable = false)
    @lombok.Builder.Default
    private BigDecimal factorToBase = BigDecimal.ONE;

    /** Unit description. */
    @Column(name = "description", length = 2000)
    private String description;

    /** Whether the unit is active; products may reference only an active base unit. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
