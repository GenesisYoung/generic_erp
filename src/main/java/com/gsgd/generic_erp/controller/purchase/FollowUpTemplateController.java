package com.gsgd.generic_erp.controller.purchase;

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
import com.gsgd.generic_erp.dto.purchase.FollowUpTemplateRequest;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;
import com.gsgd.generic_erp.service.purchase.FollowUpRuleService;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/**
 * Follow-up templates (跟进模板, {@code /api/followup-templates}): production
 * period, notification days and overdue interval. Anyone who follows up
 * orders can read them, to choose one for an order; changing them needs
 * {@code menu.followupTemplate.update}.
 */
@RestController
@RequestMapping("/api/followup-templates")
@RequiredArgsConstructor
public class FollowUpTemplateController {

    private final FollowUpRuleService service;

    @PreAuthorize("@perm.has('menu.followupTemplate.read') or @perm.has('menu.purchaseOrder.read')")
    @GetMapping
    public BasicResponse list() {
        return new BasicResponse(200, I18n.t("common.ok"), service.templates());
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PostMapping
    public BasicResponse create(@RequestBody FollowUpTemplateRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.create(body));
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PutMapping("/{id}")
    public BasicResponse update(@PathVariable Long id, @RequestBody FollowUpTemplateRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.update(id, body));
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** New purchase orders get this template. */
    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @PostMapping("/{id}/default")
    public BasicResponse makeDefault(@PathVariable Long id) {
        try {
            service.makeDefault(id);
            return new BasicResponse(200, I18n.t("common.ok"), service.templates());
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** Hides the template; orders that use it keep their tasks. */
    @PreAuthorize("@perm.has('menu.followupTemplate.update')")
    @DeleteMapping("/{id}")
    public BasicResponse remove(@PathVariable Long id) {
        try {
            service.remove(id);
            return new BasicResponse(200, I18n.t("common.ok"), service.templates());
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }
}
