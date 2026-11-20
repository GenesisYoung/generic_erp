package com.gsgd.generic_erp.service.purchase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read access to purchase orders with data scope: a caller holding
 * {@code data.purchaseOrder.all} sees every order, anyone else only orders
 * whose purchaser is themselves. A hidden order looks the same as a missing
 * one, so ids cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderQueryService {

    public static final String SCOPE_ALL = "data.purchaseOrder.all";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FollowUpCheckpointRepository checkpointRepository;
    private final PermissionChecker perm;

    public Page<PurchaseOrder> list(Long userId, Pageable pageable) {
        return perm.has(SCOPE_ALL)
                ? purchaseOrderRepository.findAll(pageable)
                : purchaseOrderRepository.findByPurchaserId(userId, pageable);
    }

    public Optional<PurchaseOrder> find(Long userId, Long id) {
        return purchaseOrderRepository.findById(id)
                .filter(po -> perm.has(SCOPE_ALL) || Objects.equals(po.getPurchaserId(), userId));
    }

    public Optional<List<FollowUpCheckpoint>> checkpoints(Long userId, Long id) {
        return find(userId, id).map(po -> checkpointRepository.findByPurchaseOrderIdOrderBySeqAsc(po.getId()));
    }
}
