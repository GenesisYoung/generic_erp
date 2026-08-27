package com.gsgd.generic_erp.util;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * LoginRateLimiter
 * A utility service that implements rate limiting for login attempts using
 * Redis.
 * This class tracks the number of login attempts for a given key within a
 * specified
 * time window and enforces a limit to prevent brute-force attacks.
 * 
 * It leverages Redis as a distributed cache to store and manage rate limit
 * counters,
 * allowing for consistent rate limiting across multiple instances in a
 * distributed system.
 */
@RequiredArgsConstructor
@Service
public class LoginRateLimiter {
    public final StringRedisTemplate redis;

    /**
     * Checks if a login attempt is allowed based on rate limiting rules.
     * 
     * @param key    the identifier for the rate limit counter (e.g., username or IP
     *               address)
     * @param limit  the maximum number of allowed attempts within the time window
     * @param window the duration for which the rate limit applies
     * @return true if the login attempt is allowed (within the limit), false
     *         otherwise
     */
    public boolean allow(String key, int limit, Duration window) {
        Long n = redis.opsForValue().increment("ratelimit:" + key);
        if (n != null && n == 1)
            redis.expire("ratelimit:" + key, window);
        return n != null && n <= limit;
    }
}
