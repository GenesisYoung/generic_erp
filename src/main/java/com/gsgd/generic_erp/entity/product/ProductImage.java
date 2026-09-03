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
 * Product-variant image association mapped to {@code product_image_tb}.
 *
 * <p>Describes how a variant uses an attached file. A variant may have at most
 * one primary image; when it changes, the service layer must clear the previous
 * primary flag within the same transaction.</p>
 */
@Entity
@Table(name = "product_image_tb",
        uniqueConstraints = @UniqueConstraint(name = "uk_variant_appendix", columnNames = {"variant_id", "appendix_id"}),
        indexes = {
                @Index(name = "idx_product_image_variant", columnList = "variant_id"),
                @Index(name = "idx_product_image_appendix", columnList = "appendix_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductImage extends ProductAuditableEntity {

    /** Image-association primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning variant identifier. */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** Attached file identifier. */
    @Column(name = "appendix_id", nullable = false)
    private Long appendixId;

    /** Alternative text used for accessibility. */
    @Column(name = "alt_text", length = 255)
    private String altText;

    /** Display order within the product gallery. */
    @Column(name = "sort_order", nullable = false)
    @lombok.Builder.Default
    private Integer sortOrder = 0;

    /** Whether this is the variant's primary image. */
    @Column(name = "is_primary", nullable = false)
    @lombok.Builder.Default
    private Boolean primary = false;
}
