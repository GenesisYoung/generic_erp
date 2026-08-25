package com.gsgd.generic_erp.configuration.message;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.gsgd.generic_erp.configuration.security.JWTUtil;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebSocketAuthConfig implements WebSocketMessageBrokerConfigurer {
    private final JWTUtil jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                // Only the CONNECT frame carries credentials. Subsequent frames
                // reuse the Principal that we attach here.
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String bearer = accessor.getFirstNativeHeader("Authorization");
                    if (bearer == null || !bearer.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Missing WebSocket credentials");
                    }
                    String token = bearer.substring(7);
                    if (token.isBlank() || !jwtService.isValid(0, token)) {
                        throw new IllegalArgumentException("Invalid WebSocket credentials");
                    }

                    // Bind the Principal to the signed token subject. The User-Name header is
                    // intentionally ignored because native STOMP headers are attacker-controlled.
                    String username = jwtService.extractUsername(0, token);
                    String sessionId = jwtService.extractSessionId(0, token);
                    UserDetails user = userDetailsService.loadUserByUsername(username);
                    if (!user.isEnabled() || !user.isAccountNonLocked()
                            || !jwtService.isSessionCurrent(username, sessionId)) {
                        throw new IllegalArgumentException("Invalid WebSocket credentials");
                    }

                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities());
                    // This is the critical line: it makes accessor.getUser()
                    // non-null, which is what /user/** destinations depend on.
                    accessor.setUser(auth);

                    return message;
                }
                return message;
            }
        });
    }

}
