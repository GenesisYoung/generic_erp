package com.gsgd.generic_erp.entity.notification;

import java.time.LocalDateTime;

import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.configuration.i18n.LocalizedTextConverter;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One notification for one user, stored so that a user who is offline when it
 * is pushed still sees it after logging in. Maps to table: notification_tb.
 * <p>
 * {@code bizType} + {@code bizId} point at the record the notification is
 * about (for example {@code PURCHASE_EXCEPTION} + its id). They are logical
 * references; like the rest of the schema there is no foreign key.
 */
@Entity
@Table(name = "notification_tb", indexes = {
        @Index(name = "idx_notification_recipient_read", columnList = "recipient_id, read_at"),
        @Index(name = "idx_notification_biz", columnList = "biz_type, biz_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    @Builder.Default
    private NotifactionPriority priority = NotifactionPriority.NORMAL;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", length = 1000)
    private String content;

    /**
     * Title as a message key with arguments, rendered in the reader's language
     * when listed or pushed. Null for notifications created with plain text;
     * then {@link #title} is shown as stored. {@link #title} always holds the
     * text in the default language as a fallback.
     */
    @Convert(converter = LocalizedTextConverter.class)
    @Column(name = "title_i18n", length = 1000)
    private LocalizedText titleText;

    /** Content as a message key with arguments; see {@link #titleText}. */
    @Convert(converter = LocalizedTextConverter.class)
    @Column(name = "content_i18n", length = 4000)
    private LocalizedText contentText;

    @Column(name = "biz_type", length = 50)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    /** null while unread. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
