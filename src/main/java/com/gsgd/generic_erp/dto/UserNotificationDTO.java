package com.gsgd.generic_erp.dto;

import java.time.LocalDateTime;

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

    public static UserNotificationDTO from(Notification n) {
        return new UserNotificationDTO(n.getId(), n.getType(), n.getPriority(), n.getTitle(),
                n.getContent(), n.getBizType(), n.getBizId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
