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

    /**
     * Id, round and status of every audit task of the given orders (not the
     * large document column); used to show each customer order's audit status.
     */
    @Query("select new com.gsgd.generic_erp.dto.customer.AuditTaskBrief(t.id, t.orderNumber, t.round, t.status)"
            + " from AuditTaskEntity t where t.orderNumber in :orderNumbers")
    java.util.List<com.gsgd.generic_erp.dto.customer.AuditTaskBrief> findBriefByOrderNumberIn(
            java.util.Collection<String> orderNumbers);
}
