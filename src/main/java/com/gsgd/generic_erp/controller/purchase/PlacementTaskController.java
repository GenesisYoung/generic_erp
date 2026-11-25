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
import com.gsgd.generic_erp.enums.PlacementOrderType;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService;
import com.gsgd.generic_erp.service.purchase.PlacementTaskService.PlacementException;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderQueryService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Purchase placement tasks (采购下单任务, {@code /api/placement-tasks}): place
 * the purchase orders of an order within 2 days. Production orders (P...) and
 * sample orders (YP...) are listed separately ({@code type}).
 * Anyone with {@code menu.purchaseOrder.update} can take a task or give it to
 * a department colleague. Holders of {@code data.purchaseOrder.all} see every
 * task; everyone else sees their department's tasks and unassigned ones.
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
     * @param type     PRODUCTION or SAMPLE; empty = both. The response also
     *                 carries the open count of each section.
     */
    @PreAuthorize("@perm.has('menu.purchaseOrder.read')")
    @GetMapping
    public BasicResponse list(@AuthenticationPrincipal UserDetail me,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PlacementOrderType orderType;
        try {
            orderType = type == null || type.isBlank() ? null
                    : PlacementOrderType.valueOf(type.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return new BasicResponse(400, I18n.t("placement.error.badType"), null);
        }
        boolean all = perm.has(PurchaseOrderQueryService.SCOPE_ALL);
        Sort sort = openOnly ? Sort.by("deadlineAt").ascending().and(Sort.by("id").ascending())
                : Sort.by("createdAt").descending().and(Sort.by("id").descending());
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        Long userId = me.getUser().getId();
        Page<PlacementTaskView> result = service.list(userId, all, openOnly, orderType, p);
        return new BasicResponse(200, I18n.t("common.ok"), Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "openCounts", service.openCounts(userId, all),
                "canAssign", perm.has("menu.purchaseOrder.update")));
    }

    /** Users the caller can give a task to: themselves and their department colleagues. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update')")
    @GetMapping("/purchasers")
    public BasicResponse purchasers(@AuthenticationPrincipal UserDetail me) {
        List<Map<String, Object>> out = service.assignable(me.getUser().getId()).entrySet().stream()
                .map(e -> Map.<String, Object>of("id", e.getKey(), "name", e.getValue()))
                .toList();
        return new BasicResponse(200, I18n.t("common.ok"), out);
    }

    /** Assigns (or re-assigns) an open task to the caller or a department colleague. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update')")
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
