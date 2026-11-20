package com.gsgd.generic_erp.controller.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.service.dashboard.DashboardService;
import com.gsgd.generic_erp.service.purchase.PurchaseOrderQueryService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/** Numbers for the role dashboards ({@code /api/dashboards}). */
@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;
    private final PermissionChecker perm;

    /** PMC dashboard: customer orders to audit, audits, placement deadlines and exceptions. */
    @PreAuthorize("@perm.has('menu.pmcDashboard')")
    @GetMapping("/pmc")
    public BasicResponse pmc() {
        return new BasicResponse(200, I18n.t("common.ok"), service.pmc());
    }

    /**
     * Purchasing dashboard. Holders of {@code data.purchaseOrder.all} see every
     * purchaser and a per-purchaser table; others see their own work.
     */
    @PreAuthorize("@perm.has('menu.purchaseDashboard')")
    @GetMapping("/purchasing")
    public BasicResponse purchasing(@AuthenticationPrincipal UserDetail me) {
        return new BasicResponse(200, I18n.t("common.ok"),
                service.purchasing(me.getUser().getId(), perm.has(PurchaseOrderQueryService.SCOPE_ALL)));
    }
}
