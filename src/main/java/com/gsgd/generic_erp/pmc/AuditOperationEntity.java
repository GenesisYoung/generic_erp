package com.gsgd.generic_erp.pmc;

import jakarta.persistence.*;

@Entity
@Table(name = "pmc_order_audit_operation")
public class AuditOperationEntity {
    @Id
    @Column(length = 160)
    public String id;
    @Column(name = "request_hash", nullable = false, length = 64)
    public String requestHash;
    @Column(name = "task_id", length = 36)
    public String taskId;
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    public String response;
}
