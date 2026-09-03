package com.gsgd.generic_erp.entity.product;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Field-level product audit log mapped to {@code product_audit_log_tb}.
 *
 * <p>
 * Field changes produced by one user action share the same
 * {@code changeGroupId}. Records represented by this entity are append-only and
 * must never be updated or deleted.
 * </p>
 */
@Entity
@Table(name = "audit_log_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "module", length = 50, nullable = false)
    private String module;

    /** CREATE / UPDATE / DELETE */
    @Column(name = "action", length = 20, nullable = false)
    private String action;

    /** Primary key of the audited business record, as text. */
    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "description", length = 255)
    private String description;

    /** Fully qualified method that was executed. */
    @Column(name = "method_name", length = 255)
    private String methodName;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "operate_time", nullable = false, updatable = false)
    private LocalDateTime operateTime;

    @Column(name = "create_date")
    private LocalDate createDate;
}