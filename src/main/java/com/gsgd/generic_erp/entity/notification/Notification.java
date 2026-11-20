package com.gsgd.generic_erp.entity.notification;

import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;

import jakarta.persistence.Column;
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
