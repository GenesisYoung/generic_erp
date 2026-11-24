package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.PurchaseOrderLine;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findByPurchaseOrderIdOrderByLineNoAsc(Long purchaseOrderId);

    List<PurchaseOrderLine> findByPurchaseOrderIdIn(Collection<Long> purchaseOrderIds);

    boolean existsByPurchaseOrderId(Long purchaseOrderId);

    @Modifying
    @Query("delete from PurchaseOrderLine l where l.purchaseOrderId = :poId")
    int deleteByPurchaseOrderId(@Param("poId") Long purchaseOrderId);

    /** Lines per plan type, for "used on N lines" on the plan group page. */
    @Query("select l.planTypeCode, count(l) from PurchaseOrderLine l where l.planTypeCode is not null group by l.planTypeCode")
    List<Object[]> countByPlanType();
}
