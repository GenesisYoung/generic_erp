package com.gsgd.generic_erp.controller.purchase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.security.UserDetail;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService;
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
        return new BasicResponse(200, "OK", followUpService.todo(me.getUser().getId()));
    }
}
