package com.gsgd.generic_erp.pmc;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "pmc_personal_evidence", indexes = @Index(name = "ix_personal_evidence_owner", columnList = "created_by,created_at"))
public class PersonalEvidenceEntity {
    @Id
    @Column(length = 36)
    public String id;
    @Column(name = "created_by", nullable = false, length = 20)
    public String createdBy;
    @Column(name = "file_name", nullable = false, length = 128)
    public String fileName;
    @Column(name = "storage_key", nullable = false, length = 64)
    public String storageKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    public String requestHash;
    @Column(name = "byte_size", nullable = false)
    public long byteSize;
    @Column(nullable = false, length = 1000)
    public String purpose;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
