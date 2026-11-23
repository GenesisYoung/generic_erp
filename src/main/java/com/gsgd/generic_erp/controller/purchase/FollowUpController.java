package com.gsgd.generic_erp.controller.purchase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.dto.purchase.FollowUpRecordRequest;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;
import com.gsgd.generic_erp.util.BasicResponse;

import lombok.RequiredArgsConstructor;

/** The purchaser's follow-up to-do list ({@code /api/followups}). */
@RestController
@RequestMapping("/api/followups")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpRecordService followUpService;

    /** My checkpoints whose window is open now; the ones ending first (due today, then overdue stage) on top. */
    @PreAuthorize("@perm.has('menu.purchaseOrder.read')")
    @GetMapping("/todo")
    public BasicResponse todo(@AuthenticationPrincipal UserDetail me) {
        return new BasicResponse(200, I18n.t("common.ok"), followUpService.todo(me.getUser().getId()));
    }

    /**
     * Marks one follow-up task (one notification) complete, saving the
     * follow-up written for it. 404 when the task or its order is not
     * visible, 400 for a bad body, 409 when the task is not open, its window
     * has not started, or the order no longer needs follow-up.
     */
    @PreAuthorize("@perm.has('menu.purchaseOrder.update')")
    @PostMapping("/checkpoints/{id}/complete")
    public BasicResponse complete(@AuthenticationPrincipal UserDetail me, @PathVariable Long id,
            @RequestBody FollowUpRecordRequest body) {
        try {
            return followUpService.completeTask(me.getUser().getId(), id, body)
                    .map(saved -> new BasicResponse(200, I18n.t("common.ok"), saved))
                    .orElseGet(() -> new BasicResponse(404, I18n.t("purchase.error.orderNotFound"), null));
        } catch (InvalidFollowUpException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }
}
