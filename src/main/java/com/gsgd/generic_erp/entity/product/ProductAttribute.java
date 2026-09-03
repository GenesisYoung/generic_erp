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
 * Product-variant attribute value mapped to {@code product_attribute_tb}.
 *
 * <p>A variant may store only one value for each attribute. Values are persisted
 * as text and parsed and validated by the service layer according to the
 * definition's {@link AttributeValueType}.</p>
 */
@Entity
@Table(name = "product_attribute_tb",
        uniqueConstraints = @UniqueConstraint(name = "uk_variant_attribute", columnNames = {"variant_id", "attr_def_id"}),
        indexes = {
                @Index(name = "idx_product_attribute_variant", columnList = "variant_id"),
                @Index(name = "idx_product_attribute_definition", columnList = "attr_def_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductAttribute extends ProductAuditableEntity {

    /** Attribute-value record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning variant identifier. */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** Attribute-definition identifier. */
    @Column(name = "attr_def_id", nullable = false)
    private Long attributeDefinitionId;

    /** Text representation of the attribute value. */
    @Column(name = "attribute_value", length = 255, nullable = false)
    private String attributeValue;

    /** Display order within the product specification sheet. */
    @Column(name = "sort_order", nullable = false)
    @lombok.Builder.Default
    private Integer sortOrder = 0;

    /** Whether the attribute value is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
