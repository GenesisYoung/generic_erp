package com.gsgd.generic_erp.entity.product;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Supplier-specific code for a product variant, mapped to
 * {@code product_supplier_code_tb}.
 *
 * <p>Stores the part number a supplier uses to identify a variant. A variant may
 * have only one sourcing record per supplier, and a supplier part number may
 * identify only one variant in that supplier's catalog.</p>
 */
@Entity
@Table(name = "product_supplier_code_tb",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_supplier_part_no", columnNames = {"supplier_id", "supplier_part_no"}),
                @UniqueConstraint(name = "uk_variant_supplier", columnNames = {"variant_id", "supplier_id"})
        },
        indexes = {
                @Index(name = "idx_product_supplier_variant", columnList = "variant_id"),
                @Index(name = "idx_product_supplier_supplier", columnList = "supplier_id"),
                @Index(name = "idx_product_supplier_part_no", columnList = "supplier_part_no")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductSupplierCode extends ProductAuditableEntity {

    /** Sourcing-record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Supplied product-variant identifier. */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** Supplier identifier. */
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** Supplier's own part number or item code. */
    @Column(name = "supplier_part_no", length = 64, nullable = false)
    private String supplierPartNumber;

    /** Whether this is the preferred source; at most one is allowed per variant. */
    @Column(name = "is_preferred", nullable = false)
    @lombok.Builder.Default
    private Boolean preferred = false;

    /** Most recent purchase cost, stored for informational purposes. */
    @Column(name = "last_purchase_cost", precision = 19, scale = 4)
    private BigDecimal lastPurchaseCost;

    /** Supplier-quoted lead time in days. */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    /** Whether this sourcing relationship is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
