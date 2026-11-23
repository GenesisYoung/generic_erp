package com.gsgd.generic_erp.entity.customer;

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

/**
 * "Purchase order X buys for customer order line Y", copied from the old ERP
 * chain PO line -> production plan line -> customer order line. Used to close
 * placement tasks and to show how many order lines already have a PO. Maps to
 * table: legacy_po_customer_link_tb.
 */
@Entity
@Table(name = "legacy_po_customer_link_tb", indexes = {
        @Index(name = "uq_po_customer_link", columnList = "customer_line_legacy_id, po_no", unique = true),
        @Index(name = "idx_po_customer_link_order", columnList = "customer_order_no")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoCustomerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ORDCORD1.ERI. */
    @Column(name = "customer_order_legacy_id", length = 32, nullable = false)
    private String customerOrderLegacyId;

    /** ORDCORD1.CODE, the order number placement tasks use. */
    @Column(name = "customer_order_no", length = 50, nullable = false)
    private String customerOrderNo;

    /** ORDCORD2.ERI. */
    @Column(name = "customer_line_legacy_id", length = 32, nullable = false)
    private String customerLineLegacyId;

    /** ORDSPHS1.CODE. */
    @Column(name = "po_no", length = 50, nullable = false)
    private String poNo;

    @Column(name = "po_date")
    private LocalDate poDate;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;
}
