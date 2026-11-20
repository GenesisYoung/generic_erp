package com.gsgd.generic_erp.pmc;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "pmc_order_audit_task", uniqueConstraints = @UniqueConstraint(name = "uq_pmc_order_round", columnNames = {
        "order_number", "review_round" }), indexes = {
                @Index(name = "ix_pmc_audit_due", columnList = "status,effective_due_at"),
                @Index(name = "ix_pmc_audit_owner", columnList = "owner_user_id"),
                @Index(name = "ix_pmc_audit_dept", columnList = "department_id"),
                @Index(name = "ix_pmc_audit_creator", columnList = "created_by"),
                @Index(name = "ix_pmc_audit_next_owner", columnList = "next_owner_user_id") })
public class AuditTaskEntity {
    @Id
    @Column(length = 36)
    public String id;
    @Column(name = "order_number", nullable = false, length = 50)
    public String orderNumber;
    @Column(name = "review_round", nullable = false)
    public int round;
    @Column(name = "owner_user_id", length = 20)
    public String ownerUserId;
    @Column(name = "created_by", nullable = false, length = 20)
    public String createdBy;
    @Column(name = "department_id", length = 20)
    public String departmentId;
    @Column(name = "next_owner_user_id", length = 20)
    public String nextOwnerUserId;
    @Column(nullable = false, length = 24)
    public String status;
    @Column(name = "accepted_at")
    public Instant acceptedAt;
    @Column(name = "effective_due_at")
    public Instant effectiveDueAt;
    @Column(name = "completed_at")
    public Instant completedAt;
    @Version
    @Column(name = "row_version", nullable = false)
    public Long rowVersion;
    @Lob
    @Column(name = "document", nullable = false, columnDefinition = "LONGTEXT")
    public String document;
}
