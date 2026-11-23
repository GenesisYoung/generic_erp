package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.PurchaseExceptionCategory;
import com.gsgd.generic_erp.enums.PurchaseExceptionSeverity;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A supplier problem on a purchase order, reported by a purchaser and handled
 * by PMC, Planning or Operations. Maps to table: purchase_exception_tb. Every
 * change is also written to purchase_exception_log_tb.
 */
@Entity
@Table(name = "purchase_exception_tb", indexes = {
        @Index(name = "idx_pexc_reporter_status", columnList = "reporter_id, status"),
        @Index(name = "idx_pexc_status", columnList = "status, reported_at"),
        @Index(name = "idx_pexc_po", columnList = "purchase_order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PurchaseException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "EX-261120-0007": date of the report plus the id. Set right after the first save. */
    @Column(name = "exception_no", length = 32)
    private String exceptionNo;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    /** Copied from the purchase order so lists need no join. */
    @Column(name = "po_no", length = 50, nullable = false)
    private String poNo;

    @Column(name = "supplier_code", length = 50)
    private String supplierCode;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 16, nullable = false)
    private PurchaseExceptionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16, nullable = false)
    private PurchaseExceptionSeverity severity;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    /** The date the problem affects, e.g. the supplier's new delivery date. */
    @Column(name = "impact_date")
    private LocalDate impactDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PurchaseExceptionStatus status;

    /** The PMC, Planning or Operations user who acknowledged it. */
    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "resolution", length = 2000)
    private String resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic lock: two handlers acting at once cannot both win. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
