package com.gsgd.generic_erp.configuration.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * The language each user's frontend last used, keyed by username. Filled on
 * every signed-in HTTP request and on WebSocket CONNECT; read when a
 * notification is pushed or listed outside a request. Kept in memory: after a
 * restart a user gets the default language until their next request.
 */
@Component
public class UserLocaleRegistry {

    private final Map<String, Locale> byUsername = new ConcurrentHashMap<>();

    public void remember(String username, Locale locale) {
        if (username != null && locale != null) {
            byUsername.put(username, locale);
        }
    }

    /** The user's last language, or null when unknown. */
    public Locale find(String username) {
        return username == null ? null : byUsername.get(username);
    }
}
