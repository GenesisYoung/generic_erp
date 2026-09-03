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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Product-specific alternative unit mapped to {@code product_uom_tb}.
 *
 * <p>Stores product-dependent packaging conversions such as
 * "1 box = 12 each."</p>
 */
@Entity
@Table(name = "product_uom_tb",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_uom", columnNames = {"product_id", "uom_id"}),
        indexes = {
                @Index(name = "idx_product_uom_product", columnList = "product_id"),
                @Index(name = "idx_product_uom_uom", columnList = "uom_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductUnitOfMeasure extends ProductAuditableEntity {

    /** Conversion record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Product identifier. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Alternative unit identifier. */
    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    /** Positive number of base units contained in one alternative unit. */
    @Column(name = "factor_to_base_uom", precision = 24, scale = 10, nullable = false)
    private BigDecimal factorToBaseUom;

    /** Purchasing or sales context in which the alternative unit is valid. */
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false)
    @lombok.Builder.Default
    private ProductUomUsageType usageType = ProductUomUsageType.BOTH;

    /** Whether this conversion is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
