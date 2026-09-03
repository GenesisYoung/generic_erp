package com.gsgd.generic_erp.repository.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.product.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
