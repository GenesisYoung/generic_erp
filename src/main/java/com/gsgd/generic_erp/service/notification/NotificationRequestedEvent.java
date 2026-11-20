package com.gsgd.generic_erp.service.notification;

import java.util.Collection;
import java.util.Set;

import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;

/**
 * Published by a business service (with {@code ApplicationEventPublisher}) when
 * some users must be told about a change. {@link NotificationDispatcher} picks
 * it up only after the publishing transaction commits, so a rolled-back save
 * never produces a notification.
 *
 * @param recipientIds user ids; duplicates are ignored
 * @param titleText    optional; when set, the title is shown in each reader's
 *                     language and {@code title} is only the stored fallback
 * @param contentText  optional; same as {@code titleText} for the content
 */
public record NotificationRequestedEvent(
        NotificationType type,
        NotifactionPriority priority,
        String title,
        String content,
        String bizType,
        Long bizId,
        Set<Long> recipientIds,
        LocalizedText titleText,
        LocalizedText contentText) {

    public NotificationRequestedEvent {
        recipientIds = recipientIds == null ? Set.of() : Set.copyOf(recipientIds);
        priority = priority == null ? NotifactionPriority.NORMAL : priority;
    }

    /** A notification with plain text, shown the same to every reader. */
    public NotificationRequestedEvent(NotificationType type, NotifactionPriority priority, String title,
            String content, String bizType, Long bizId, Set<Long> recipientIds) {
        this(type, priority, title, content, bizType, bizId, recipientIds, null, null);
    }

    /**
     * A notification whose title and content are shown in each reader's
     * language. The stored fallback text uses the default language.
     */
    public static NotificationRequestedEvent localized(NotificationType type, NotifactionPriority priority,
            LocalizedText title, LocalizedText content, String bizType, Long bizId, Collection<Long> recipientIds) {
        java.util.Locale fallback = com.gsgd.generic_erp.configuration.i18n.I18n.defaultLocale();
        return new NotificationRequestedEvent(type, priority, title.render(fallback),
                content == null ? null : content.render(fallback), bizType, bizId,
                recipientIds == null ? Set.of() : Set.copyOf(recipientIds), title, content);
    }

    public static NotificationRequestedEvent of(NotificationType type, NotifactionPriority priority,
            String title, String content, String bizType, Long bizId, Collection<Long> recipientIds) {
        return new NotificationRequestedEvent(type, priority, title, content, bizType, bizId,
                recipientIds == null ? Set.of() : Set.copyOf(recipientIds));
    }
}
