package com.gsgd.generic_erp.entity.product;

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
 * Product-to-category association mapped to {@code product_category_rel_tb}.
 *
 * <p>A product may belong to multiple categories but may have only one primary
 * category. When the primary category changes, the service layer must clear the
 * previous primary flag within the same transaction.</p>
 */
@Entity
@Table(name = "product_category_rel_tb",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_category", columnNames = {"product_id", "cate_id"}),
        indexes = {
                @Index(name = "idx_product_category_product", columnList = "product_id"),
                @Index(name = "idx_product_category_category", columnList = "cate_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductCategoryRelation extends ProductAuditableEntity {

    /** Association primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Product identifier. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Category identifier. */
    @Column(name = "cate_id", nullable = false)
    private Long cateId;

    /** Whether this is the primary category used for breadcrumbs and reporting. */
    @Column(name = "is_primary", nullable = false)
    @lombok.Builder.Default
    private Boolean primary = false;

    /** Whether the association is active; false represents a soft deletion. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
