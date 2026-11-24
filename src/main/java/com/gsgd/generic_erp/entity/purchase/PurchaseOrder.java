package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.PurchaseOrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A purchase order. For now every order is created in the old ERP and copied
 * here by the sync job (decided 2026-11-20); {@code sourceSystem} +
 * {@code externalPoNo} identify the original. Maps to table: purchase_order_tb.
 */
@Entity
@Table(name = "purchase_order_tb", uniqueConstraints = @UniqueConstraint(name = "uk_po_source_no", columnNames = {
        "source_system", "external_po_no" }), indexes = {
                @Index(name = "idx_po_purchaser_status", columnList = "purchaser_id, status"),
                @Index(name = "idx_po_status_order_date", columnList = "status, order_date") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** e.g. LEGACY_ERP. */
    @Column(name = "source_system", length = 30, nullable = false)
    private String sourceSystem;

    /** The order number in the source system. */
    @Column(name = "external_po_no", length = 50, nullable = false)
    private String externalPoNo;

    @Column(name = "supplier_code", length = 50)
    private String supplierCode;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    /** Buyer code as the old ERP stores it. */
    @Column(name = "buyer_code", length = 50)
    private String buyerCode;

    /** user_tb.id of the purchaser; null when the buyer code is not mapped yet. */
    @Column(name = "purchaser_id")
    private Long purchaserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private PurchaseOrderStatus status;

    /** Raw status text from the source, kept for checking the mapping. */
    @Column(name = "source_status", length = 50)
    private String sourceStatus;

    /** Date the order was placed; follow-up day 1 is the day after. */
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "followup_rule_id")
    private Long followupRuleId;

    /**
     * False when every open line of the order is followed up in a separate
     * plan-group task, so the main task has nothing left to follow. Null
     * (orders synced before plan groups existed) counts as true.
     */
    @Column(name = "main_followup_active")
    private Boolean mainFollowupActive;

    /** Last-change time of the row in the source system. */
    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** True while the main follow-up task is needed (see {@link #mainFollowupActive}). */
    public boolean isMainFollowupNeeded() {
        return !Boolean.FALSE.equals(mainFollowupActive);
    }
}
