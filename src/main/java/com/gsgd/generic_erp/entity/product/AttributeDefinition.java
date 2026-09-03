package com.gsgd.generic_erp.entity.product;

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
 * Product attribute definition mapped to {@code attribute_def_tb}.
 *
 * <p>Centralizes attribute names and types to prevent free-text variants such as
 * Colour and Color from creating duplicate concepts.</p>
 */
@Entity
@Table(name = "attribute_def_tb", indexes =
        @Index(name = "idx_attribute_code", columnList = "attribute_code", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AttributeDefinition extends ProductAuditableEntity {

    /** Attribute-definition primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attr_def_id")
    private Long attributeDefinitionId;

    /** Stable, globally unique attribute code, for example COLOUR. */
    @Column(name = "attribute_code", length = 64, nullable = false, unique = true)
    private String attributeCode;

    /** Attribute label displayed in the user interface. */
    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    /** Data type of the attribute value. */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    @lombok.Builder.Default
    private AttributeValueType valueType = AttributeValueType.TEXT;

    /** Optional unit identifier for the attribute value. */
    @Column(name = "uom_id")
    private Long uomId;

    /** Whether this attribute distinguishes variants, such as color or size. */
    @Column(name = "is_variant_axis", nullable = false)
    @lombok.Builder.Default
    private Boolean variantAxis = false;

    /** Whether the attribute definition is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
