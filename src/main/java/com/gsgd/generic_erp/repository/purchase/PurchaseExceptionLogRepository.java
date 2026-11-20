package com.gsgd.generic_erp.repository.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.PurchaseExceptionLog;

public interface PurchaseExceptionLogRepository extends JpaRepository<PurchaseExceptionLog, Long> {

    List<PurchaseExceptionLog> findByExceptionIdOrderByCreatedAtAscIdAsc(Long exceptionId);
}
