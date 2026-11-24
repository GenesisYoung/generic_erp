package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.PurchaseExceptionApprovalStatus;

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

/**
 * A PMC approval task: created each time a handler proposes a resolution for a
 * purchasing exception. Any member of the PMC department approves (the
 * exception becomes RESOLVED) or rejects it with a reason (the exception goes
 * back to IN_PROGRESS). Maps to table: purchase_exception_approval_tb.
 */
@Entity
@Table(name = "purchase_exception_approval_tb", indexes = {
        @Index(name = "idx_pexc_appr_exception", columnList = "exception_id, requested_at"),
        @Index(name = "idx_pexc_appr_status", columnList = "status, requested_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseExceptionApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exception_id", nullable = false)
    private Long exceptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PurchaseExceptionApprovalStatus status;

    /** The resolution the handler proposed. */
    @Column(name = "proposed_resolution", length = 2000, nullable = false)
    private String proposedResolution;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** The PMC member who approved or rejected it. */
    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Optional note when approving; the reason when rejecting. */
    @Column(name = "decision_comment", length = 2000)
    private String decisionComment;
}
