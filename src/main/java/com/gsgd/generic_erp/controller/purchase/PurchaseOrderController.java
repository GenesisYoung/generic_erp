package com.gsgd.generic_erp.controller.purchase;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderQueryService;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderSyncService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicResponse;

/**
 * Purchase orders copied from the old ERP and their follow-up checkpoints
 * ({@code /api/purchase-orders}). Lists are filtered by data scope in
 * {@link PurchaseOrderQueryService}.
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseOrderQueryService queryService;
    private final PurchaseOrderSyncService syncService;

    public PurchaseOrderController(PurchaseOrderQueryService queryService, PurchaseOrderSyncService syncService) {
        this.queryService = queryService;
        this.syncService = syncService;
    }

    /** A page of purchase orders the caller may see, newest order date first. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.read')")
    @GetMapping("/fetch")
    public BasicResponse fetch(@AuthenticationPrincipal UserDetail me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BasicPage p = new BasicPage(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        p.defineSort(Sort.by("orderDate").descending().and(Sort.by("id").descending()));
        Page<PurchaseOrder> result = queryService.list(me.getUser().getId(), p);
        return new BasicResponse(200, "OK", Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber()));
    }

    /** The follow-up checkpoints of one order. 404 when it does not exist or is not visible. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.read')")
    @GetMapping("/{id}/checkpoints")
    public BasicResponse checkpoints(@AuthenticationPrincipal UserDetail me, @PathVariable Long id) {
        return queryService.checkpoints(me.getUser().getId(), id)
                .map(list -> new BasicResponse(200, "OK", list))
                .orElseGet(() -> new BasicResponse(404, "Purchase order not found", null));
    }

    /** Result of the last sync from the old ERP. */
    @PreAuthorize("@perm.has('data.purchaseOrder.all')")
    @GetMapping("/sync/status")
    public BasicResponse syncStatus() {
        return new BasicResponse(200, "OK", syncService.lastState().orElse(null));
    }

    /** Runs a sync from the old ERP now, without waiting for the schedule. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update') and @perm.has('data.purchaseOrder.all')")
    @PostMapping("/sync")
    public BasicResponse syncNow() {
        try {
            return new BasicResponse(200, "OK", syncService.syncNow());
        } catch (Exception e) {
            syncService.recordFailure(e);
            return new BasicResponse(502, "Sync from the old ERP failed; see sync status", null);
        }
    }
}
