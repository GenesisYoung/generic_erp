package com.gsgd.generic_erp.service.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gsgd.generic_erp.configuration.message.NotificationService;
import com.gsgd.generic_erp.dto.UserNotificationDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.notification.Notification;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.notification.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Stores one {@link Notification} row per recipient and then pushes it over
 * WebSocket.
 * <p>
 * Runs after the publishing transaction commits ({@code AFTER_COMMIT}), or
 * straight away when there is no transaction ({@code fallbackExecution}), on
 * the async executor so the caller's request is not slowed down. The rows are
 * saved first: if the push fails (user offline, socket closed) the user still
 * finds the notification in the list next time.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        dispatch(event);
    }

    /** Saves and pushes the notification. Returns the rows that were saved. */
    public List<Notification> dispatch(NotificationRequestedEvent event) {
        if (event.recipientIds().isEmpty()) {
            return List.of();
        }
        // Only active accounts are notified.
        Map<Long, User> recipients = userRepository.findAllById(event.recipientIds()).stream()
                .filter(u -> Boolean.TRUE.equals(u.getStatus()))
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (recipients.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> rows = recipients.keySet().stream()
                .map(userId -> Notification.builder()
                        .recipientId(userId)
                        .type(event.type())
                        .priority(event.priority())
                        .title(event.title())
                        .content(event.content())
                        .bizType(event.bizType())
                        .bizId(event.bizId())
                        .createdAt(now)
                        .build())
                .toList();
        List<Notification> saved = notificationRepository.saveAll(rows);

        for (Notification n : saved) {
            User user = recipients.get(n.getRecipientId());
            try {
                notificationService.sendUserNotification(user.getUsername(), UserNotificationDTO.from(n));
            } catch (Exception e) {
                // Stored already; the user sees it on the next list refresh.
                log.warn("Push failed for notification {} to user {}", n.getId(), user.getId(), e);
            }
        }
        return saved;
    }
}
