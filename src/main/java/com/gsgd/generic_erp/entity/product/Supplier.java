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
 * Supplier master-data entity mapped to {@code supplier_tb}.
 *
 * <p>The Product module provisionally owns this data. A future Purchasing module
 * may take ownership and extend the supplier master.</p>
 */
@Entity
@Table(name = "supplier_tb", indexes = {
        @Index(name = "idx_supplier_code", columnList = "supplier_code", unique = true),
        @Index(name = "idx_supplier_legal_name", columnList = "legal_name", unique = true),
        @Index(name = "idx_supplier_trade_name", columnList = "trade_name"),
        @Index(name = "idx_supplier_status", columnList = "status"),
        @Index(name = "idx_supplier_tax_id", columnList = "tax_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Supplier extends ProductAuditableEntity {

    /** Supplier primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long supplierId;

    /** Human-readable, globally unique supplier code. */
    @Column(name = "supplier_code", length = 20, nullable = false, unique = true)
    private String supplierCode;

    /** Legal entity name used on tax and payment documents. */
    @Column(name = "legal_name", length = 256, nullable = false, unique = true)
    private String legalName;

    /** Trade name under which the supplier conducts business. */
    @Column(name = "trade_name", length = 255)
    private String tradeName;

    /** Current operational status of the supplier. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @lombok.Builder.Default
    private SupplierStatus status = SupplierStatus.ACTIVE;

    /** EIN, VAT, or local tax registration number; null until verified. */
    @Column(name = "tax_id", length = 50, unique = true)
    private String taxId;
}
