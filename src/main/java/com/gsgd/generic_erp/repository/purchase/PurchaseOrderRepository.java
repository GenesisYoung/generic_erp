package com.gsgd.generic_erp.repository.purchase;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findBySourceSystemAndExternalPoNo(String sourceSystem, String externalPoNo);

    Page<PurchaseOrder> findByPurchaserId(Long purchaserId, Pageable pageable);

    /**
     * Orders in one of the statuses, placed before the day, with no checkpoint
     * window that reaches the day: they have run past their planned stages.
     */
    @Query("""
            SELECT po FROM PurchaseOrder po
            WHERE po.status IN :statuses AND po.orderDate < :day
              AND NOT EXISTS (SELECT 1 FROM FollowUpCheckpoint c
                              WHERE c.purchaseOrderId = po.id AND c.windowEnd >= :day)""")
    List<PurchaseOrder> findWithoutWindowReaching(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
            @Param("day") LocalDate day);
}
