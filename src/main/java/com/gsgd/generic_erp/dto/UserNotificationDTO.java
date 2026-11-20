package com.gsgd.generic_erp.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.entity.notification.Notification;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;

/**
 * A stored notification as the frontend sees it, both in the REST list and in
 * the WebSocket push to {@code /user/queue/notifications}. The two use the same
 * shape so the client can merge them into one list by {@code id}.
 */
public record UserNotificationDTO(
        Long id,
        NotificationType type,
        NotifactionPriority priority,
        String title,
        String content,
        String bizType,
        Long bizId,
        boolean read,
        LocalDateTime createdAt) {

    /** In the current request's language. */
    public static UserNotificationDTO from(Notification n) {
        return from(n, I18n.currentLocale());
    }

    /**
     * Title and content in {@code locale} when the notification was stored as
     * keys; otherwise the stored text.
     */
    public static UserNotificationDTO from(Notification n, Locale locale) {
        String title = n.getTitleText() != null ? I18n.t(locale, n.getTitleText()) : n.getTitle();
        String content = n.getContentText() != null ? I18n.t(locale, n.getContentText()) : n.getContent();
        return new UserNotificationDTO(n.getId(), n.getType(), n.getPriority(), title,
                content, n.getBizType(), n.getBizId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
