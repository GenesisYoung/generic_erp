package com.gsgd.generic_erp.entity.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A customer order copied from the old ERP (ORDCORD1 header), so PMC can see
 * which new orders still need an order audit. Maps to table:
 * legacy_customer_order_tb.
 * <p>
 * The old ERP stays the system of record; this row is a read-only copy plus
 * {@link #firstSeenAt} (when this system first saw it). Whether an order audit
 * exists is not stored here: it is looked up from pmc_order_audit_task by
 * {@link #orderNo}, so it is always current.
 */
@Entity
@Table(name = "legacy_customer_order_tb", indexes = {
        @Index(name = "uq_customer_order_legacy_id", columnList = "legacy_id", unique = true),
        @Index(name = "idx_customer_order_no", columnList = "order_no"),
        @Index(name = "idx_customer_order_date", columnList = "order_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** ORDCORD1.ERI. */
    @Column(name = "legacy_id", length = 32, nullable = false)
    private String legacyId;

    /** ORDCORD1.CODE; the number PMC types when creating an order audit. */
    @Column(name = "order_no", length = 50, nullable = false)
    private String orderNo;

    @Column(name = "customer_code", length = 50)
    private String customerCode;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /** ORDCORD1.STAFF (salesperson code). */
    @Column(name = "sales_code", length = 50)
    private String salesCode;

    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "total_qty", precision = 18, scale = 4)
    private BigDecimal totalQty;

    /** Audited in the old ERP (SCRUTINY filled). Informational only. */
    @Column(name = "legacy_audited", nullable = false)
    private boolean legacyAudited;

    /** Closed in the old ERP (CLOSETYPE not 0). A closed order needs no audit. */
    @Column(name = "legacy_closed", nullable = false)
    private boolean legacyClosed;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;
}
