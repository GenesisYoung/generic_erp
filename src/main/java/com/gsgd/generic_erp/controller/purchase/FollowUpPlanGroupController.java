package com.gsgd.generic_erp.controller.purchase;

import java.util.function.Supplier;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.PlanGroupRequest;
import com.gsgd.generic_erp.dto.purchase.PlanTypeRequest;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanGroupService;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Plan groups ({@code /api/followup-plan-groups}): which purchase plan types
 * count as the same plan and which are followed up as their own task. They
 * sit on the follow-up template page, so they use its permissions.
 */
@RestController
@RequestMapping("/api/followup-plan-groups")
@RequiredArgsConstructor
public class FollowUpPlanGroupController {

    private final FollowUpPlanGroupService service;

    @PreAuthorize("@perm.has('menu.followupTemplate.read') or @perm.has('menu.purchaseOrder.read')")
    @GetMapping
    public BasicResponse overview() {
        return ok(service::overview);
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PostMapping
    public BasicResponse create(@RequestBody PlanGroupRequest body) {
        return ok(() -> service.createGroup(body));
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PutMapping("/{id}")
    public BasicResponse update(@PathVariable Long id, @RequestBody PlanGroupRequest body) {
        return ok(() -> service.updateGroup(id, body));
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @DeleteMapping("/{id}")
    public BasicResponse remove(@PathVariable Long id) {
        return ok(() -> service.removeGroup(id));
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PostMapping("/types")
    public BasicResponse addType(@RequestBody PlanTypeRequest body) {
        return ok(() -> service.addType(body));
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PutMapping("/types/{id}")
    public BasicResponse updateType(@PathVariable Long id, @RequestBody PlanTypeRequest body) {
        return ok(() -> service.updateType(id, body));
    }

    /** Re-splits every open order's follow-up with the current groups; answers what changed. */
    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PostMapping("/apply")
    public BasicResponse apply() {
        return ok(service::applyToOpenOrders);
    }

    private static BasicResponse ok(Supplier<Object> action) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), action.get());
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }
}
