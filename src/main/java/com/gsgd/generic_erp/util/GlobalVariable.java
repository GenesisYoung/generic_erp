package com.gsgd.generic_erp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.gsgd.generic_erp.repository.auth.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Application-wide configuration values injected from properties.
 * Currently exposes the default response language ({@code EN} / {@code CN}),
 * sourced from the {@code LANGUAGE} environment variable.
 */
@Component
@RequiredArgsConstructor
public class GlobalVariable {

    private final UserRepository userRepository;

    @Value("${spring.global.variable.default-language}")
    private String DEFAULT_LANGUAGE;

    public String getDEFAULT_LANGUAGE() {
        return DEFAULT_LANGUAGE;
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
            return details.getUsername();
        }
        return null;
    }

    public Long currentUserId() {
        String username = currentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
    }

    public String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

}
