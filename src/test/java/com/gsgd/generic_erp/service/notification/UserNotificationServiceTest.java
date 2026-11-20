package com.gsgd.generic_erp.service.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.entity.notification.Notification;
import com.gsgd.generic_erp.repository.notification.NotificationRepository;

class UserNotificationServiceTest {

    private NotificationRepository repository;
    private UserNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new UserNotificationService(repository);
    }

    @Test
    void marksOwnNotificationRead() {
        Notification n = Notification.builder().id(5L).recipientId(1L).title("t").build();
        when(repository.findByIdAndRecipientId(5L, 1L)).thenReturn(Optional.of(n));

        assertTrue(service.markRead(1L, 5L));
        assertNotNull(n.getReadAt());
        verify(repository).save(n);
    }

    @Test
    void cannotMarkSomeoneElsesNotification() {
        when(repository.findByIdAndRecipientId(5L, 2L)).thenReturn(Optional.empty());

        assertFalse(service.markRead(2L, 5L));
        verify(repository, never()).save(any());
    }
}
