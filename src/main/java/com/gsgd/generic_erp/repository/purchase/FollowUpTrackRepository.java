package com.gsgd.generic_erp.repository.purchase;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpTrack;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;

public interface FollowUpTrackRepository extends JpaRepository<FollowUpTrack, Long> {

    List<FollowUpTrack> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    List<FollowUpTrack> findByPurchaseOrderIdIn(Collection<Long> purchaseOrderIds);

    Optional<FollowUpTrack> findByPurchaseOrderIdAndPlanGroupId(Long purchaseOrderId, Long planGroupId);

    /** Active tracks per plan group, for "open in N orders" on the plan group page. */
    @Query("""
            select t.planGroupId, count(t) from FollowUpTrack t, PurchaseOrder po
            where po.id = t.purchaseOrderId and t.active = true and po.status in :statuses
            group by t.planGroupId""")
    List<Object[]> countActiveByGroup(@Param("statuses") Collection<PurchaseOrderStatus> statuses);

    /** Tracks that use a template, for "used by N orders" on the template page. */
    @Query("""
            select t.followupRuleId, count(t) from FollowUpTrack t, PurchaseOrder po
            where po.id = t.purchaseOrderId and t.active = true and po.status in :statuses
              and t.followupRuleId is not null
            group by t.followupRuleId""")
    List<Object[]> countActiveByRule(@Param("statuses") Collection<PurchaseOrderStatus> statuses);

    /**
     * Active tracks of active orders placed before the day that have no
     * checkpoint of their group reaching the day: they ran past their plan.
     */
    @Query("""
            select t from FollowUpTrack t, PurchaseOrder po
            where po.id = t.purchaseOrderId and t.active = true and po.status in :statuses and po.orderDate < :day
              and not exists (select 1 from FollowUpCheckpoint c
                              where c.purchaseOrderId = po.id and c.planGroupId = t.planGroupId
                                and c.windowEnd >= :day)""")
    List<FollowUpTrack> findWithoutWindowReaching(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
            @Param("day") LocalDate day);
}
