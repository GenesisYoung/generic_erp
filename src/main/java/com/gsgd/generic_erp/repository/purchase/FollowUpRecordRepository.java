package com.gsgd.generic_erp.repository.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.FollowUpRecord;

public interface FollowUpRecordRepository extends JpaRepository<FollowUpRecord, Long> {

    List<FollowUpRecord> findByPurchaseOrderIdOrderByFollowedUpAtDesc(Long purchaseOrderId);
}
