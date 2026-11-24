package com.gsgd.generic_erp.repository.purchase;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.PurchaseExceptionApproval;
import com.gsgd.generic_erp.enums.PurchaseExceptionApprovalStatus;

public interface PurchaseExceptionApprovalRepository extends JpaRepository<PurchaseExceptionApproval, Long> {

    /** The approval task still waiting for PMC, if any (at most one per exception). */
    Optional<PurchaseExceptionApproval> findFirstByExceptionIdAndStatusOrderByIdDesc(Long exceptionId,
            PurchaseExceptionApprovalStatus status);

    /** Every approval task of the exception, oldest first. */
    List<PurchaseExceptionApproval> findByExceptionIdOrderByIdAsc(Long exceptionId);

    long countByStatus(PurchaseExceptionApprovalStatus status);
}
