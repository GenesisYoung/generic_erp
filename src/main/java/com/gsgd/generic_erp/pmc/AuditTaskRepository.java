package com.gsgd.generic_erp.pmc;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface AuditTaskRepository
        extends JpaRepository<AuditTaskEntity, String>, JpaSpecificationExecutor<AuditTaskEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuditTaskEntity t where t.id = :id")
    Optional<AuditTaskEntity> lockById(String id);

    boolean existsByOrderNumberAndRound(String orderNumber, int round);
}
