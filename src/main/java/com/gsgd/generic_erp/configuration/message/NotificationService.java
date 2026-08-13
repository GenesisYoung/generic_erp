package com.gsgd.generic_erp.configuration.message;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.GeneralAlertDTO;
import com.gsgd.generic_erp.dto.NotificationDTO;
import com.gsgd.generic_erp.dto.StatusNotificationDTO;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate simpleMessageService;

    /**
     * User notifications that only send to a specific user, e.g., for a specific
     * event or action
     */
    public void sendNotification(String username, NotificationDTO payload) {
        simpleMessageService.convertAndSendToUser(username, "/queue/notifications", payload);
    }

    /**
     * Build connection between server and client to update front-end status
     */
    public void statusUpdates(String userName, StatusNotificationDTO payload) {
        simpleMessageService.convertAndSendToUser(userName, "/system/status", payload);
    }

    /**
     * Broadcast notifications that send to all users
     */
    public void generalAlert(GeneralAlertDTO dto) {
        simpleMessageService.convertAndSend("/topic/general-alert", dto);
    }

}
