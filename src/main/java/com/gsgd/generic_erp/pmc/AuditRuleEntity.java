package com.gsgd.generic_erp.pmc;

import jakarta.persistence.*;

@Entity
@Table(name = "pmc_order_audit_rule")
public class AuditRuleEntity {
    @Id
    public Integer id;
    @Version
    @Column(name = "row_version", nullable = false)
    public Long rowVersion;
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    public String document;
}
