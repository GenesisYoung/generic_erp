package com.gsgd.generic_erp.repository.purchase;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.enums.CheckpointStatus;

public interface FollowUpCheckpointRepository extends JpaRepository<FollowUpCheckpoint, Long> {

    List<FollowUpCheckpoint> findByPurchaseOrderIdOrderBySeqAsc(Long purchaseOrderId);

    boolean existsByPurchaseOrderId(Long purchaseOrderId);

    @Modifying
    @Query("UPDATE FollowUpCheckpoint c SET c.status = :to WHERE c.purchaseOrderId = :poId AND c.status IN :from")
    int changeStatus(@Param("poId") Long purchaseOrderId, @Param("from") Collection<CheckpointStatus> from,
            @Param("to") CheckpointStatus to);
}
