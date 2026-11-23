package com.gsgd.generic_erp.configuration.i18n;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Records the signed-in caller's language in {@link UserLocaleRegistry}. */
class UserLocaleInterceptor implements HandlerInterceptor {

    private final UserLocaleRegistry registry;

    UserLocaleInterceptor(UserLocaleRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && request.getHeader("Accept-Language") != null) {
            registry.remember(auth.getName(), LocaleContextHolder.getLocale());
        }
        return true;
    }
}
