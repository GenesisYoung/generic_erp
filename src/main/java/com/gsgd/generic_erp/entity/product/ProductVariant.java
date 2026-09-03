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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sellable product variant mapped to {@code product_variant_tb}.
 *
 * <p>
 * Information tied to a concrete sellable item, including SKU, barcodes,
 * weight, dimensions, and images, belongs to a variant. A simple product must
 * still own one default variant.
 * </p>
 */
@Entity
@Table(name = "product_variant_tb", uniqueConstraints = @UniqueConstraint(name = "uk_variant_product_name", columnNames = {
                "product_id", "variant_name" }), indexes = {
                                @Index(name = "idx_variant_product", columnList = "product_id"),
                                @Index(name = "idx_variant_sku", columnList = "sku", unique = true),
                                @Index(name = "idx_variant_name", columnList = "variant_name"),
                                @Index(name = "idx_variant_weight_uom", columnList = "weight_uom_id"),
                                @Index(name = "idx_variant_size_uom", columnList = "size_uom_id")
                })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductVariant extends ProductAuditableEntity {

        /** Variant primary key. */
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "variant_id")
        private Long variantId;

        /** Parent product identifier. */
        @Column(name = "product_id", nullable = false)
        private Long productId;

        /** Globally unique, immutable sellable SKU. */
        @Column(name = "sku", length = 64, nullable = false, unique = true, updatable = false)
        private String sku;

        /** Variant display name; unique within its product. */
        @Column(name = "variant_name", length = 64, nullable = false)
        private String variantName;

        /**
         * Whether this is the default variant; true for a simple product's implicit
         * variant.
         */
        @Column(name = "is_default", nullable = false)
        @lombok.Builder.Default
        private Boolean defaultVariant = false;

        /** Variant-specific description. */
        @Column(name = "description", length = 2000)
        private String description;

        /** Variant standard-cost override; the product cost applies when null. */
        @Column(name = "standard_cost", precision = 19, scale = 4)
        private BigDecimal standardCost;

        /** Variant list-price override; the product price applies when null. */
        @Column(name = "list_price", precision = 19, scale = 4)
        private BigDecimal listPrice;

        /** Three-letter ISO 4217 code for variant-level prices. */
        @Column(name = "currency", length = 3)
        private String currency;

        /** Net weight, which must be non-negative. */
        @Column(name = "net_weight", precision = 19, scale = 4)
        private BigDecimal netWeight;

        /** Net-weight unit identifier, which must belong to the MASS dimension. */
        @Column(name = "weight_uom_id")
        private Long weightUomId;

        /** Packed length stored in millimeters. */
        @Column(name = "length", precision = 12, scale = 3)
        private BigDecimal length;

        /** Packed width stored in millimeters. */
        @Column(name = "width", precision = 12, scale = 3)
        private BigDecimal width;

        /** Packed height stored in millimeters. */
        @Column(name = "height", precision = 12, scale = 3)
        private BigDecimal height;

        /** Size unit identifier, which must belong to the LENGTH dimension. */
        @Column(name = "size_uom_id")
        private Long sizeUomId;

        /** Whether the variant is active; false represents a soft deletion. */
        @Column(name = "active", nullable = false)
        @lombok.Builder.Default
        private Boolean active = true;

        /** Hibernate optimistic-lock version. */
        @Version
        @Column(name = "version", nullable = false)
        private Integer version;
}
