package com.gsgd.generic_erp.entity.product;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Field-level product audit log mapped to {@code product_audit_log_tb}.
 *
 * <p>Field changes produced by one user action share the same
 * {@code changeGroupId}. Records represented by this entity are append-only and
 * must never be updated or deleted.</p>
 */
@Entity
@Table(name = "product_audit_log_tb", indexes = {
        @Index(name = "idx_audit_change_group", columnList = "change_group_id"),
        @Index(name = "idx_audit_operation", columnList = "operation"),
        @Index(name = "idx_audit_affected_row", columnList = "affected_table, affected_row"),
        @Index(name = "idx_audit_edited_by", columnList = "edited_by"),
        @Index(name = "idx_audit_edit_time", columnList = "edit_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAuditLog {

    /** Audit-log primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID that groups field changes produced by the same transaction. */
    @Column(name = "change_group_id", length = 36, nullable = false, updatable = false)
    private String changeGroupId;

    /** Operation that produced the change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, updatable = false)
    private AuditOperation operation;

    /** Name of the affected database table. */
    @Column(name = "affected_table", length = 64, nullable = false, updatable = false)
    private String affectedTable;

    /** Text representation of the affected row's primary key. */
    @Column(name = "affected_row", length = 64, nullable = false, updatable = false)
    private String affectedRow;

    /** Name of the affected database column. */
    @Column(name = "affected_column", length = 64, nullable = false, updatable = false)
    private String affectedColumn;

    /** Value before the change; null for a CREATE operation. */
    @Column(name = "before_val", columnDefinition = "TEXT", updatable = false)
    private String beforeValue;

    /** Value after the change; null for a DELETE operation. */
    @Column(name = "after_val", columnDefinition = "TEXT", updatable = false)
    private String afterValue;

    /** Identifier of the user who made the change. */
    @Column(name = "edited_by", nullable = false, updatable = false)
    private Long editedBy;

    /** Time at which the change occurred. */
    @Column(name = "edit_time", nullable = false, updatable = false)
    private LocalDateTime editTime;

    /** Initializes the audit timestamp before insertion. */
    @PrePersist
    void onCreate() {
        if (editTime == null) {
            editTime = LocalDateTime.now();
        }
    }
}
