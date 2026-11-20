package com.gsgd.generic_erp.service.notification;

import java.util.Collection;
import java.util.Set;

import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;

/**
 * Published by a business service (with {@code ApplicationEventPublisher}) when
 * some users must be told about a change. {@link NotificationDispatcher} picks
 * it up only after the publishing transaction commits, so a rolled-back save
 * never produces a notification.
 *
 * @param recipientIds user ids; duplicates are ignored
 */
public record NotificationRequestedEvent(
        NotificationType type,
        NotifactionPriority priority,
        String title,
        String content,
        String bizType,
        Long bizId,
        Set<Long> recipientIds) {

    public NotificationRequestedEvent {
        recipientIds = recipientIds == null ? Set.of() : Set.copyOf(recipientIds);
        priority = priority == null ? NotifactionPriority.NORMAL : priority;
    }

    public static NotificationRequestedEvent of(NotificationType type, NotifactionPriority priority,
            String title, String content, String bizType, Long bizId, Collection<Long> recipientIds) {
        return new NotificationRequestedEvent(type, priority, title, content, bizType, bizId,
                recipientIds == null ? Set.of() : Set.copyOf(recipientIds));
    }
}
