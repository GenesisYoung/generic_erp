package com.gsgd.generic_erp.pmc;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface AuditRuleRepository extends JpaRepository<AuditRuleEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AuditRuleEntity r where r.id = 1")
    Optional<AuditRuleEntity> lockCurrent();
}
