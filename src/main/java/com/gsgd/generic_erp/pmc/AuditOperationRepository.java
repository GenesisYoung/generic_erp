package com.gsgd.generic_erp.pmc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditOperationRepository extends JpaRepository<AuditOperationEntity, String> {
}
