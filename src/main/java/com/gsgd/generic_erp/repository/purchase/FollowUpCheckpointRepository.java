package com.gsgd.generic_erp.repository.purchase;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.enums.CheckpointStatus;

public interface FollowUpCheckpointRepository extends JpaRepository<FollowUpCheckpoint, Long> {

    List<FollowUpCheckpoint> findByPurchaseOrderIdOrderBySeqAsc(Long purchaseOrderId);

    boolean existsByPurchaseOrderId(Long purchaseOrderId);

    /** Removes the order's OPEN checkpoints (they have no record yet) before it is re-planned. */
    @Modifying
    @Query("DELETE FROM FollowUpCheckpoint c WHERE c.purchaseOrderId = :poId AND c.status = :status")
    int deleteByPurchaseOrderIdAndStatus(@Param("poId") Long purchaseOrderId,
            @Param("status") CheckpointStatus status);

    @Modifying
    @Query("UPDATE FollowUpCheckpoint c SET c.status = :to WHERE c.purchaseOrderId = :poId AND c.status IN :from")
    int changeStatus(@Param("poId") Long purchaseOrderId, @Param("from") Collection<CheckpointStatus> from,
            @Param("to") CheckpointStatus to);

    List<FollowUpCheckpoint> findByPurchaseOrderIdAndPlanGroupIdAndStatus(Long purchaseOrderId, Long planGroupId,
            CheckpointStatus status);

    /** Removes the OPEN checkpoints of one follow-up task series (null group = main task) before it is re-planned. */
    @Modifying
    @Query("""
            DELETE FROM FollowUpCheckpoint c WHERE c.purchaseOrderId = :poId AND c.status = :status
              AND ((:groupId IS NULL AND c.planGroupId IS NULL) OR c.planGroupId = :groupId)""")
    int deleteOpenOfGroup(@Param("poId") Long purchaseOrderId, @Param("groupId") Long planGroupId,
            @Param("status") CheckpointStatus status);

    /** Changes the status of one task series' checkpoints (null group = main task). */
    @Modifying
    @Query("""
            UPDATE FollowUpCheckpoint c SET c.status = :to WHERE c.purchaseOrderId = :poId AND c.status IN :from
              AND ((:groupId IS NULL AND c.planGroupId IS NULL) OR c.planGroupId = :groupId)""")
    int changeStatusOfGroup(@Param("poId") Long purchaseOrderId, @Param("groupId") Long planGroupId,
            @Param("from") Collection<CheckpointStatus> from, @Param("to") CheckpointStatus to);

    /** The OPEN checkpoint of one task series whose window contains the day (null group = main task). */
    @Query("""
            SELECT c FROM FollowUpCheckpoint c
            WHERE c.purchaseOrderId = :poId AND c.status = com.gsgd.generic_erp.enums.CheckpointStatus.OPEN
              AND c.windowStart <= :day AND c.windowEnd >= :day
              AND ((:groupId IS NULL AND c.planGroupId IS NULL) OR c.planGroupId = :groupId)
            ORDER BY c.seq ASC LIMIT 1""")
    Optional<FollowUpCheckpoint> findOpenCoveringInGroup(@Param("poId") Long purchaseOrderId,
            @Param("groupId") Long planGroupId, @Param("day") LocalDate day);

    /** The OPEN checkpoint of an order whose window contains the given day. */
    @Query("""
            SELECT c FROM FollowUpCheckpoint c
            WHERE c.purchaseOrderId = :poId AND c.status = com.gsgd.generic_erp.enums.CheckpointStatus.OPEN
              AND c.windowStart <= :day AND c.windowEnd >= :day
            ORDER BY c.seq ASC LIMIT 1""")
    Optional<FollowUpCheckpoint> findOpenCovering(@Param("poId") Long purchaseOrderId, @Param("day") LocalDate day);

    /** OPEN checkpoints whose window contains the day and that have not been reminded yet. */
    @Query("""
            SELECT new com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder(c, po)
            FROM FollowUpCheckpoint c, PurchaseOrder po
            WHERE po.id = c.purchaseOrderId AND c.status = com.gsgd.generic_erp.enums.CheckpointStatus.OPEN
              AND c.windowStart <= :day AND c.windowEnd >= :day AND c.remindedAt IS NULL
            ORDER BY po.purchaserId, c.windowEnd, po.externalPoNo""")
    List<CheckpointAndOrder> findDueNotReminded(@Param("day") LocalDate day);

    /** OPEN checkpoints whose window ended before the day. */
    @Query("""
            SELECT new com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder(c, po)
            FROM FollowUpCheckpoint c, PurchaseOrder po
            WHERE po.id = c.purchaseOrderId AND c.status = com.gsgd.generic_erp.enums.CheckpointStatus.OPEN
              AND c.windowEnd < :day
            ORDER BY po.purchaserId, c.windowEnd, po.externalPoNo""")
    List<CheckpointAndOrder> findOpenEndedBefore(@Param("day") LocalDate day);

    /** A purchaser's OPEN checkpoints whose window has started by the day, the ones ending first on top. */
    @Query("""
            SELECT new com.gsgd.generic_erp.repository.purchase.CheckpointAndOrder(c, po)
            FROM FollowUpCheckpoint c, PurchaseOrder po
            WHERE po.id = c.purchaseOrderId AND po.purchaserId = :userId
              AND c.status = com.gsgd.generic_erp.enums.CheckpointStatus.OPEN AND c.windowStart <= :day
            ORDER BY c.windowEnd, po.externalPoNo""")
    List<CheckpointAndOrder> findTodo(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Query("SELECT COALESCE(MAX(c.seq), 0) FROM FollowUpCheckpoint c WHERE c.purchaseOrderId = :poId")
    int maxSeq(@Param("poId") Long purchaseOrderId);
}
