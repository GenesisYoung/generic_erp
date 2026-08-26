package com.gsgd.generic_erp.configuration.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompSessionRegistry {
    private final Map<String, Map<String, String>> byUser = new ConcurrentHashMap<>(); // user -> (stompSessionId ->
                                                                                       // sid)

    @EventListener
    public void onConnect(SessionConnectedEvent e) {
        /* record user, stomp session id, sid */

    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        /* remove */ }

    public void closeAllExcept(String username, String currentSid, SimpMessagingTemplate template) {
        byUser.getOrDefault(username, Map.of()).forEach((stompId, sid) -> {
            if (!sid.equals(currentSid)) {
                StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.ERROR);
                h.setSessionId(stompId);
                h.setMessage("Session superseded");
                template.convertAndSendToUser(username, "/system/status",
                        new byte[0], h.getMessageHeaders()); // an ERROR frame closes the socket
            }
        });
    }
}
