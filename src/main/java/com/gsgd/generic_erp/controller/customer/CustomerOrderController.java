package com.gsgd.generic_erp.controller.customer;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.customer.CustomerOrderView;
import com.gsgd.generic_erp.service.customer.CustomerOrderQueryService;
import com.gsgd.generic_erp.service.customer.CustomerOrderSyncService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Customer orders copied from the old ERP ({@code /api/customer-orders}), for
 * PMC to see which new orders still need an order audit.
 */
@RestController
@RequestMapping("/api/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerOrderQueryService queryService;
    private final CustomerOrderSyncService syncService;

    /**
     * A page of customer orders, newest order date first.
     *
     * @param view {@code pending} (default): open orders with no order audit;
     *             {@code all}: every synced order, searchable with {@code q}
     */
    @PreAuthorize("@perm.has('menu.customerOrder.read')")
    @GetMapping
    public BasicResponse list(
            @RequestParam(defaultValue = "pending") String view,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("orderDate").descending().and(Sort.by("id").descending()));
        Page<CustomerOrderView> result = "all".equalsIgnoreCase(view) ? queryService.all(q, p)
                : queryService.pending(p);
        return new BasicResponse(200, I18n.t("common.ok"), Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "pendingCount", queryService.pendingCount()));
    }

    /** Result of the last customer order sync from the old ERP. */
    @PreAuthorize("@perm.has('menu.customerOrder.read')")
    @GetMapping("/sync/status")
    public BasicResponse syncStatus() {
        return new BasicResponse(200, I18n.t("common.ok"),
                syncService.lastState().orElse(null));
    }

    /** Runs the sync now, without waiting for the schedule. */
    @PreAuthorize("@perm.has('menu.customerOrder.update')")
    @PostMapping("/sync")
    public BasicResponse syncNow() {
        try {
            CustomerOrderSyncService.SyncResult r = syncService.syncNow();
            if ("DISABLED".equals(r.status())) {
                return new BasicResponse(409,
                        I18n.t("customerOrder.error.syncDisabled"), r);
            }
            return new BasicResponse(200, I18n.t("common.ok"), r);
        } catch (Exception e) {
            syncService.recordFailure(e);
            return new BasicResponse(502, I18n.t("customerOrder.error.syncFailed"), null);
        }
    }
}
