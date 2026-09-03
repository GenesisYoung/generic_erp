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
 * Product-variant barcode mapped to {@code product_barcode_tb}.
 *
 * <p>The barcode image is generated on demand rather than persisted. The service
 * layer must validate length, format, and check digits according to
 * {@link BarcodeType}.</p>
 */
@Entity
@Table(name = "product_barcode_tb", indexes = {
        @Index(name = "idx_barcode_variant", columnList = "variant_id"),
        @Index(name = "idx_barcode_value", columnList = "barcode_value", unique = true),
        @Index(name = "idx_barcode_region", columnList = "region")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductBarcode extends ProductAuditableEntity {

    /** Barcode record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning variant identifier. */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** Actual barcode value, unique across the system. */
    @Column(name = "barcode_value", length = 64, nullable = false, unique = true)
    private String barcodeValue;

    /** Barcode symbology. */
    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", nullable = false)
    private BarcodeType barcodeType;

    /** Primary market region; null when the barcode is not region-specific. */
    @Enumerated(EnumType.STRING)
    @Column(name = "region")
    private BarcodeRegion region;

    /** Whether this is the variant's primary display barcode. */
    @Column(name = "is_primary", nullable = false)
    @lombok.Builder.Default
    private Boolean primary = false;

    /** Whether the barcode is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
