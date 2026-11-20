package com.gsgd.generic_erp.service.notification;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.dto.UserNotificationDTO;
import com.gsgd.generic_erp.repository.notification.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read side of notifications for the logged-in user: list, unread count, and
 * marking as read. The caller's user id is always passed in by the controller
 * from the authenticated principal, never taken from the request.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final NotificationRepository notificationRepository;

    public Page<UserNotificationDTO> list(Long userId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByRecipientId(userId, pageable);
        return page.map(UserNotificationDTO::from);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    /** Marks one of the user's notifications as read. False when it is not theirs or does not exist. */
    @Transactional
    public boolean markRead(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .map(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(LocalDateTime.now());
                        notificationRepository.save(n);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }
}
