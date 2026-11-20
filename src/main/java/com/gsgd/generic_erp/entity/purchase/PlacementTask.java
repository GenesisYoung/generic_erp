package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

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
 * "Place the purchase orders for customer order X within 2 calendar days of
 * its order audit passing." One row per verified order audit. Maps to table:
 * po_placement_task_tb.
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
        @Index(name = "idx_placement_order_no", columnList = "order_no")
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** pmc_order_audit_task.id of the verified audit round. */
    @Column(name = "audit_task_id", length = 36, nullable = false)
    private String auditTaskId;

    @Column(name = "audit_round", nullable = false)
    private int auditRound;

    @Column(name = "order_no", length = 50, nullable = false)
    private String orderNo;

    /** PASS, FAIL, ... as verified. Only PASS creates an OPEN task. */
    @Column(name = "audit_result", length = 16)
    private String auditResult;

    /** When the audit was verified, in the business zone. The 2-day clock starts here. */
    @Column(name = "audit_verified_at", nullable = false)
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

    /** LINKED_PO or MANUAL. */
    @Column(name = "done_source", length = 16)
    private String doneSource;

    /** The purchase order that closed the task. */
    @Column(name = "done_po_no", length = 50)
    private String donePoNo;

    /** user_tb.id for MANUAL; null for LINKED_PO. */
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
