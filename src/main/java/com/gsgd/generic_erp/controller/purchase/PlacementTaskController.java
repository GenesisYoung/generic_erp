package com.gsgd.generic_erp.controller.purchase;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.dto.purchase.PlacementAssignRequest;
import com.gsgd.generic_erp.dto.purchase.PlacementCompleteRequest;
import com.gsgd.generic_erp.dto.purchase.PlacementTaskView;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService.PlacementException;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderQueryService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Purchase placement tasks (采购下单任务, {@code /api/placement-tasks}): place
 * the purchase orders of a customer order within 2 days of its audit passing.
 * Holders of {@code data.purchaseOrder.all} (purchasing managers) see and
 * assign every task; a purchaser sees the tasks assigned to them.
 */
@RestController
@RequestMapping("/api/placement-tasks")
@RequiredArgsConstructor
public class PlacementTaskController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlacementTaskService service;
    private final PermissionChecker perm;

    /**
     * A page of tasks, nearest deadline first.
     *
     * @param openOnly true (default): open tasks only; false: open and done
     */
    @PreAuthorize("@perm.has('menu.purchaseOrder.read')")
    @GetMapping
    public BasicResponse list(@AuthenticationPrincipal UserDetail me,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean manager = perm.has(PurchaseOrderQueryService.SCOPE_ALL);
        Sort sort = openOnly ? Sort.by("deadlineAt").ascending().and(Sort.by("id").ascending())
                : Sort.by("auditVerifiedAt").descending().and(Sort.by("id").descending());
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        Page<PlacementTaskView> result = service.list(me.getUser().getId(), manager, openOnly, p);
        return new BasicResponse(200, I18n.t("common.ok"), Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "canAssign", manager));
    }

    /** Users who can be given a task (role purchaser). */
    @PreAuthorize("@perm.has('data.purchaseOrder.all')")
    @GetMapping("/purchasers")
    public BasicResponse purchasers() {
        List<Map<String, Object>> out = service.purchasers().entrySet().stream()
                .map(e -> Map.<String, Object>of("id", e.getKey(), "name", e.getValue()))
                .toList();
        return new BasicResponse(200, I18n.t("common.ok"), out);
    }

    /** The purchasing manager assigns (or re-assigns) an open task to a purchaser. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update') and @perm.has('data.purchaseOrder.all')")
    @PostMapping("/{id}/assign")
    public BasicResponse assign(@AuthenticationPrincipal UserDetail me, @PathVariable Long id,
            @RequestBody PlacementAssignRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"),
                    service.assign(id, body == null ? null : body.assigneeId(), me.getUser().getId()));
        } catch (PlacementException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /**
     * Closes an open task with a PO number typed by hand, for when the old ERP
     * has no link from the PO to the customer order.
     */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update')")
    @PostMapping("/{id}/complete")
    public BasicResponse complete(@AuthenticationPrincipal UserDetail me, @PathVariable Long id,
            @RequestBody PlacementCompleteRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.completeManually(id,
                    body == null ? null : body.poNo(), me.getUser().getId(),
                    perm.has(PurchaseOrderQueryService.SCOPE_ALL)));
        } catch (PlacementException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }
}
