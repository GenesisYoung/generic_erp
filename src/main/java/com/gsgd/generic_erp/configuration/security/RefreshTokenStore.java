package com.gsgd.generic_erp.configuration.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Stores only SHA-256 refresh-token fingerprints and rotates them atomically. */
@Service
public class RefreshTokenStore {

    private static final String PREFIX = "refreshToken:";
    private static final DefaultRedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current ~= ARGV[1] then
              return 'MISMATCH'
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 'OK'
            """, String.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration lifetime;

    public RefreshTokenStore(RedisTemplate<String, Object> redisTemplate,
            @Value("${security.token.expiracy.refresh}") long lifetimeMs) {
        if (lifetimeMs <= 0) {
            throw new IllegalArgumentException("Refresh-token lifetime must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.lifetime = Duration.ofMillis(lifetimeMs);
    }

    public void store(String username, String token) {
        redisTemplate.opsForValue().set(key(username), fingerprint(token), lifetime);
    }

    /** Atomically consumes the presented token and replaces it with the new token. */
    public boolean rotate(String username, String presentedToken, String newToken) {
        String result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(username)),
                fingerprint(presentedToken),
                fingerprint(newToken),
                String.valueOf(lifetime.toMillis()));
        return "OK".equals(result);
    }

    public void revoke(String username) {
        redisTemplate.delete(key(username));
    }

    private String key(String username) {
        return PREFIX + username;
    }

    static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
