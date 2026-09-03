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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Product master-data entity mapped to {@code product_tb}.
 *
 * <p>
 * This entity stores information shared by all variants. Every product must
 * own at least one {@link ProductVariant}; the service layer must enforce this
 * invariant within the same transaction.
 * </p>
 */
@Entity
@Table(name = "product_tb", indexes = {
        @Index(name = "idx_product_code", columnList = "product_code", unique = true),
        @Index(name = "idx_product_name", columnList = "product_name"),
        @Index(name = "idx_product_type", columnList = "type"),
        @Index(name = "idx_product_status", columnList = "status"),
        @Index(name = "idx_product_base_uom", columnList = "base_uom_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends ProductAuditableEntity {

    /** Product primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    /** Immutable product code; shares one logical namespace with all SKUs. */
    @Column(name = "product_code", length = 64, nullable = false, unique = true, updatable = false)
    private String productCode;

    /** Product display name. */
    @Column(name = "product_name", length = 150, nullable = false)
    private String productName;

    /** Detailed product description. */
    @Column(name = "product_description", length = 2000)
    private String productDescription;

    /** Product business type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ProductType type;

    /** Whether the product has multiple user-visible variants. */
    @Column(name = "configurable", nullable = false)
    @lombok.Builder.Default
    private Boolean configurable = false;

    /** Product lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @lombok.Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    /** Base unit identifier used to express inventory and cost. */
    @Column(name = "base_uom_id", nullable = false)
    private Long baseUomId;

    /** Standard cost; null means unpriced, while zero means free. */
    @Column(name = "standard_cost", precision = 19, scale = 4)
    private BigDecimal standardCost;

    /** List price; null means unpriced, while zero means free. */
    @Column(name = "list_price", precision = 19, scale = 4)
    private BigDecimal listPrice;

    /** Three-letter ISO 4217 currency code; required when any price is set. */
    @Column(name = "currency", length = 3)
    private String currency;

    /** Tax-category label reserved for the future Tax module. */
    @Column(name = "tax_category", length = 32)
    private String taxCategory;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** Hibernate optimistic-lock version used to prevent concurrent overwrites. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
