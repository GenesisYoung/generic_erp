package com.gsgd.generic_erp.repository.purchase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.PlacementTask;
import com.gsgd.generic_erp.enums.PlacementOrderType;
import com.gsgd.generic_erp.enums.PlacementSource;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;

public interface PlacementTaskRepository extends JpaRepository<PlacementTask, Long> {

    /** Verified audit rounds that have no placement task row yet (any status). */
    @Query("""
            select t.id from AuditTaskEntity t
            where t.status = 'VERIFIED'
              and not exists (select 1 from PlacementTask p where p.auditTaskId = t.id)
            """)
    List<String> findVerifiedAuditIdsWithoutTask();

    boolean existsByOrderNoAndStatusIn(String orderNo, Collection<PlacementTaskStatus> statuses);

    List<PlacementTask> findByStatus(PlacementTaskStatus status);

    /** Tasks already made from these old-ERP documents (any status). */
    List<PlacementTask> findBySourceAndSourceNoIn(PlacementSource source, Collection<String> sourceNos);

    /** True when an order-audit task for the order is still open (made before the switch to confirmations). */
    @Query("""
            select count(p) > 0 from PlacementTask p
            where p.orderNo = :orderNo
              and p.status = com.gsgd.generic_erp.enums.PlacementTaskStatus.OPEN
              and (p.source is null or p.source = com.gsgd.generic_erp.enums.PlacementSource.ORDER_AUDIT)
            """)
    boolean existsOpenAuditTask(@Param("orderNo") String orderNo);

    List<PlacementTask> findByStatusAndOrderNoIn(PlacementTaskStatus status, Collection<String> orderNos);

    /** Open tasks due within the reminder window that were not reminded yet. */
    @Query("""
            select p from PlacementTask p
            where p.status = com.gsgd.generic_erp.enums.PlacementTaskStatus.OPEN
              and p.remindedAt is null and p.deadlineAt > :now and p.deadlineAt <= :until
            """)
    List<PlacementTask> findDueSoonNotReminded(LocalDateTime now, LocalDateTime until);

    @Query("""
            select p from PlacementTask p
            where p.status = com.gsgd.generic_erp.enums.PlacementTaskStatus.OPEN
              and p.overdueNotifiedAt is null and p.deadlineAt <= :now
            """)
    List<PlacementTask> findOverdueNotNotified(LocalDateTime now);

    Page<PlacementTask> findByStatusInAndOrderTypeIn(Collection<PlacementTaskStatus> statuses,
            Collection<PlacementOrderType> orderTypes, Pageable pageable);

    /** Tasks of the given order types assigned to one of the given users, or to nobody yet. */
    @Query("""
            select t from PlacementTask t
            where t.status in :statuses and t.orderType in :orderTypes
              and (t.assigneeId is null or t.assigneeId in :userIds)
            """)
    Page<PlacementTask> findForDepartment(@Param("userIds") Collection<Long> userIds,
            @Param("statuses") Collection<PlacementTaskStatus> statuses,
            @Param("orderTypes") Collection<PlacementOrderType> orderTypes, Pageable pageable);

    /** Open tasks per order type (for the section badges). */
    @Query("""
            select t.orderType, count(t) from PlacementTask t
            where t.status = com.gsgd.generic_erp.enums.PlacementTaskStatus.OPEN
              and (:all = true or t.assigneeId is null or t.assigneeId in :userIds)
            group by t.orderType
            """)
    List<Object[]> countOpenByOrderType(@Param("all") boolean all, @Param("userIds") Collection<Long> userIds);

    Page<PlacementTask> findByAssigneeIdAndStatusIn(Long assigneeId, Collection<PlacementTaskStatus> statuses,
            Pageable pageable);
}
