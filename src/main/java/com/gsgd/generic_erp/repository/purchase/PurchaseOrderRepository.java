package com.gsgd.generic_erp.repository.purchase;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findBySourceSystemAndExternalPoNo(String sourceSystem, String externalPoNo);

    Page<PurchaseOrder> findByPurchaserId(Long purchaserId, Pageable pageable);
}
