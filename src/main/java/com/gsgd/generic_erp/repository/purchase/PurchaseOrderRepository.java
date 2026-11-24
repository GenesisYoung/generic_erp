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

    List<PurchaseOrder> findByExternalPoNo(String externalPoNo);

    List<PurchaseOrder> findBySourceSystemAndExternalPoNoIn(String sourceSystem, Collection<String> externalPoNos);

    List<PurchaseOrder> findByStatusIn(Collection<PurchaseOrderStatus> statuses);

    /**
     * Orders in one of the statuses, placed before the day, whose main
     * follow-up task is still needed and has no checkpoint window that reaches
     * the day: they have run past their planned stages. Separate plan-group
     * tasks are checked by {@code FollowUpTrackRepository}.
     */
    @Query("""
            SELECT po FROM PurchaseOrder po
            WHERE po.status IN :statuses AND po.orderDate < :day
              AND (po.mainFollowupActive IS NULL OR po.mainFollowupActive = true)
              AND NOT EXISTS (SELECT 1 FROM FollowUpCheckpoint c
                              WHERE c.purchaseOrderId = po.id AND c.planGroupId IS NULL AND c.windowEnd >= :day)""")
    List<PurchaseOrder> findWithoutWindowReaching(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
            @Param("day") LocalDate day);
}
