package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.PurchaseException;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;

public interface PurchaseExceptionRepository extends JpaRepository<PurchaseException, Long> {

    /** Every exception in the given statuses (users with data.purchaseException.all). */
    Page<PurchaseException> findByStatusIn(Collection<PurchaseExceptionStatus> statuses, Pageable pageable);

    /** Exceptions the user reported, or on purchase orders the user is the purchaser of. */
    @Query("""
            select e from PurchaseException e
            where e.status in :statuses
              and (e.reporterId = :userId
                   or exists (select 1 from PurchaseOrder p where p.id = e.purchaseOrderId and p.purchaserId = :userId))
            """)
    Page<PurchaseException> findVisibleTo(@Param("userId") Long userId,
            @Param("statuses") Collection<PurchaseExceptionStatus> statuses, Pageable pageable);

    long countByStatusIn(Collection<PurchaseExceptionStatus> statuses);
}
