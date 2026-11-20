package com.gsgd.generic_erp.controller.notification;

import com.gsgd.generic_erp.configuration.i18n.I18n;
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
import com.gsgd.generic_erp.dto.UserNotificationDTO;
import com.gsgd.generic_erp.service.notification.UserNotificationService;
import com.gsgd.generic_erp.util.BasicPage;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * The logged-in user's own notifications ({@code /api/notifications}). Every
 * user may call these: the service only ever touches rows whose recipient is
 * the caller.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationService service;

    public NotificationController(UserNotificationService service) {
        this.service = service;
    }

    /** A page of my notifications, newest first. */
    @GetMapping("/fetch")
    public BasicResponse fetch(@AuthenticationPrincipal UserDetail me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        BasicPage p = new BasicPage(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        p.defineSort(Sort.by("id").descending());
        Page<UserNotificationDTO> result = service.list(me.getUser().getId(), unreadOnly, p);
        return new BasicResponse(200, I18n.t("common.ok"), Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber()));
    }

    @GetMapping("/unread-count")
    public BasicResponse unreadCount(@AuthenticationPrincipal UserDetail me) {
        return new BasicResponse(200, I18n.t("common.ok"), service.unreadCount(me.getUser().getId()));
    }

    @PostMapping("/{id}/read")
    public SimpleResponse markRead(@AuthenticationPrincipal UserDetail me, @PathVariable Long id) {
        return service.markRead(me.getUser().getId(), id)
                ? new SimpleResponse(200, I18n.t("common.ok"))
                : new SimpleResponse(404, I18n.t("notification.error.notFound"));
    }

    @PostMapping("/read-all")
    public BasicResponse markAllRead(@AuthenticationPrincipal UserDetail me) {
        return new BasicResponse(200, I18n.t("common.ok"), service.markAllRead(me.getUser().getId()));
    }
}
