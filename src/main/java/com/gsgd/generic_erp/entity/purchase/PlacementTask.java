package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.PlacementOrderType;
import com.gsgd.generic_erp.enums.PlacementSource;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * "Place the purchase orders for order X within 2 calendar days." Maps to
 * table: po_placement_task_tb.
 * <p>
 * Since 2026-11-24 a task is made for each old-ERP document that is not yet
 * confirmed for purchase: a purchase application (采购申请) for a sample order
 * (YP...) or a purchase plan (采购计划) for a production order (P...). One row
 * per (source, source document, order). Older rows came from order audits
 * ({@code source = ORDER_AUDIT}, one row per verified audit round).
 * <p>
 * The purchasing manager assigns a purchaser. The task closes by itself when
 * the old ERP shows the first purchase order linked to the order
 * ({@code doneSource = LINKED_PO}), or when someone enters the PO number by
 * hand ({@code MANUAL}).
 */
@Entity
@Table(name = "po_placement_task_tb", indexes = {
        @Index(name = "uq_placement_audit_task", columnList = "audit_task_id", unique = true),
        @Index(name = "idx_placement_status_deadline", columnList = "status, deadline_at"),
        @Index(name = "idx_placement_assignee", columnList = "assignee_id, status"),
        @Index(name = "idx_placement_order_no", columnList = "order_no"),
        @Index(name = "uq_placement_source_doc", columnList = "source, source_no, order_no", unique = true),
        @Index(name = "idx_placement_type_status", columnList = "order_type, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PlacementTask {

    public static final String SOURCE_LINKED_PO = "LINKED_PO";
    public static final String SOURCE_MANUAL = "MANUAL";
    /** The old ERP marked the plan / application as confirmed for purchase. */
    public static final String SOURCE_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** pmc_order_audit_task.id of the verified audit round; null unless source is ORDER_AUDIT. */
    @Column(name = "audit_task_id", length = 36)
    private String auditTaskId;

    /** Null unless source is ORDER_AUDIT. */
    @Column(name = "audit_round")
    private Integer auditRound;

    @Column(name = "order_no", length = 50, nullable = false)
    private String orderNo;

    /** PRODUCTION (P...), SAMPLE (YP...) or OTHER; decides the page section. */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 16)
    private PlacementOrderType orderType;

    /** What created the task. Null on rows older than V13 means ORDER_AUDIT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 24)
    private PlacementSource source;

    /** Old-ERP document number of the plan / application; the audit task id for ORDER_AUDIT. */
    @Column(name = "source_no", length = 50)
    private String sourceNo;

    /** When the 2-day clock started: the document date, or the audit verification time. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** PASS, FAIL, ... as verified (ORDER_AUDIT only). Only PASS creates an OPEN task. */
    @Column(name = "audit_result", length = 16)
    private String auditResult;

    /** When the audit was verified, in the business zone (ORDER_AUDIT only). */
    @Column(name = "audit_verified_at")
    private LocalDateTime auditVerifiedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PlacementTaskStatus status;

    /** user_tb.id of the purchaser; null until the manager assigns one. */
    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    /** LINKED_PO, MANUAL or CONFIRMED. */
    @Column(name = "done_source", length = 16)
    private String doneSource;

    /** The purchase order that closed the task. */
    @Column(name = "done_po_no", length = 50)
    private String donePoNo;

    /** user_tb.id for MANUAL; null for LINKED_PO and CONFIRMED. */
    @Column(name = "done_by")
    private Long doneBy;

    /** Set when the "due soon" reminder was sent. */
    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    /** Set when the overdue alert was sent. */
    @Column(name = "overdue_notified_at")
    private LocalDateTime overdueNotifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
