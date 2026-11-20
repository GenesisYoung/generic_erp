package com.gsgd.generic_erp.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.configuration.message.NotificationService;
import com.gsgd.generic_erp.dto.UserNotificationDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.notification.Notification;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.notification.NotificationRepository;

class NotificationDispatcherTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        dispatcher = new NotificationDispatcher(notificationRepository, userRepository, notificationService);

        AtomicLong ids = new AtomicLong(100);
        when(notificationRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<Notification> saved = new ArrayList<>();
            for (Notification n : inv.<Iterable<Notification>>getArgument(0)) {
                n.setId(ids.incrementAndGet());
                saved.add(n);
            }
            return saved;
        });
    }

    private static User user(long id, String name, boolean active) {
        return User.builder().id(id).username(name).password("x").status(active).build();
    }

    private static NotificationRequestedEvent event(Set<Long> recipients) {
        return new NotificationRequestedEvent(NotificationType.EXCEPTION_REPORTED, NotifactionPriority.HIGH,
                "Supplier delay on PO-001", "Material late by 5 days", "PURCHASE_EXCEPTION", 7L, recipients);
    }

    @Test
    void savesAndPushesOneNotificationPerActiveRecipient() {
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1, "alice", true), user(2, "bob", true)));

        List<Notification> saved = dispatcher.dispatch(event(Set.of(1L, 2L)));

        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(n -> n.getReadAt() == null && n.getCreatedAt() != null));
        assertTrue(saved.stream().allMatch(n -> n.getBizId() == 7L && "PURCHASE_EXCEPTION".equals(n.getBizType())));
        verify(notificationService).sendUserNotification(eq("alice"), any(UserNotificationDTO.class));
        verify(notificationService).sendUserNotification(eq("bob"), any(UserNotificationDTO.class));
    }

    @Test
    void skipsDisabledAccounts() {
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1, "alice", true), user(2, "bob", false)));

        List<Notification> saved = dispatcher.dispatch(event(Set.of(1L, 2L)));

        assertEquals(1, saved.size());
        assertEquals(1L, saved.get(0).getRecipientId());
        verify(notificationService, never()).sendUserNotification(eq("bob"), any());
    }

    @Test
    void noRecipientsMeansNoWrites() {
        assertTrue(dispatcher.dispatch(event(Set.of())).isEmpty());
        verify(notificationRepository, never()).saveAll(anyIterable());
    }

    @Test
    void aFailedPushDoesNotStopTheOthers() {
        when(userRepository.findAllById(any())).thenReturn(List.of(user(1, "alice", true), user(2, "bob", true)));
        doThrow(new IllegalStateException("socket closed"))
                .when(notificationService).sendUserNotification(eq("alice"), any());

        List<Notification> saved = dispatcher.dispatch(event(Set.of(1L, 2L)));

        assertEquals(2, saved.size());
        verify(notificationService, times(2)).sendUserNotification(any(), any());
    }
}
