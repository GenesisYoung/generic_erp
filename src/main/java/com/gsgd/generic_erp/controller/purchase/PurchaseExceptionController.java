package com.gsgd.generic_erp.controller.purchase;

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
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionActionRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionReportRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionView;
import com.gsgd.generic_erp.service.purchase.PurchaseExceptionService;
import com.gsgd.generic_erp.service.purchase.PurchaseExceptionService.Actor;
import com.gsgd.generic_erp.service.purchase.PurchaseExceptionService.PurchaseExceptionError;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderQueryService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Purchasing exceptions (采购异常, {@code /api/purchase-exceptions}).
 * Holders of {@code data.purchaseException.all} see every exception; anyone
 * else sees the ones they reported or that are on their purchase orders.
 */
@RestController
@RequestMapping("/api/purchase-exceptions")
@RequiredArgsConstructor
public class PurchaseExceptionController {

    static final String SCOPE_ALL = "data.purchaseException.all";
    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseExceptionService service;
    private final PermissionChecker perm;

    private Actor actor(UserDetail me) {
        boolean all = perm.has(SCOPE_ALL);
        return new Actor(me.getUser().getId(), all, all && perm.has("menu.purchaseException.update"),
                perm.has("menu.purchaseException.create"), perm.has(PurchaseOrderQueryService.SCOPE_ALL));
    }

    /** A page of exceptions, newest first. {@code openOnly} (default true): not yet resolved. */
    @PreAuthorize("@perm.has('menu.purchaseException.read')")
    @GetMapping
    public BasicResponse list(@AuthenticationPrincipal UserDetail me,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Actor a = actor(me);
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("reportedAt").descending().and(Sort.by("id").descending()));
        Page<PurchaseExceptionView> result = service.list(a, openOnly, p);
        return new BasicResponse(200, I18n.t("common.ok"), Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "openCount", service.openCount(a),
                "canReport", a.reports()));
    }

    /** One exception with its history. */
    @PreAuthorize("@perm.has('menu.purchaseException.read')")
    @GetMapping("/{id}")
    public BasicResponse detail(@AuthenticationPrincipal UserDetail me, @PathVariable Long id) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.detail(actor(me), id));
        } catch (PurchaseExceptionError e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** Reports an exception on a purchase order and alerts PMC, Planning, Operations and purchasing at once. */
    @PreAuthorize("@perm.has('menu.purchaseException.create')")
    @PostMapping
    public BasicResponse report(@AuthenticationPrincipal UserDetail me,
            @RequestBody PurchaseExceptionReportRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.report(actor(me), body));
        } catch (PurchaseExceptionError e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** Acknowledge, start, comment, resolve, close or reopen; the service checks who may do what. */
    @PreAuthorize("@perm.has('menu.purchaseException.read')")
    @PostMapping("/{id}/actions")
    public BasicResponse act(@AuthenticationPrincipal UserDetail me, @PathVariable Long id,
            @RequestBody PurchaseExceptionActionRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.act(actor(me), id, body));
        } catch (PurchaseExceptionError e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            return new BasicResponse(409, I18n.t("exception.error.stale"), null);
        }
    }
}
