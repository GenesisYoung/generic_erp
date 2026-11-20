package com.gsgd.generic_erp.repository.purchase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.gsgd.generic_erp.entity.purchase.PlacementTask;
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

    Page<PlacementTask> findByStatusIn(Collection<PlacementTaskStatus> statuses, Pageable pageable);

    Page<PlacementTask> findByAssigneeIdAndStatusIn(Long assigneeId, Collection<PlacementTaskStatus> statuses,
            Pageable pageable);
}
