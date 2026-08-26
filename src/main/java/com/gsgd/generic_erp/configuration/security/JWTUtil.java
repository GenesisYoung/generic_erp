package com.gsgd.generic_erp.configuration.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.repository.auth.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Utility service for creating, parsing, and validating JWTs.
 * <p>
 * Two independent HMAC-SHA256 keys are used: one for short-lived access tokens
 * and one for long-lived refresh tokens. The keys are read from the
 * {@code AUTHENTICATION_SECRET_KEY_ACCESS} /
 * {@code AUTHENTICATION_SECRET_KEY_REFRESH}
 * environment variables, falling back to hard-coded development keys when
 * absent.
 * Token lifetimes are configured via {@code security.token.expiracy.*}
 * properties.
 */
@Service
public class JWTUtil {

    private final SecretKey keyRefresh;
    private final SecretKey keyAccess;

    @Value("${security.token.expiracy.refresh}")
    private long refreshTokenExpirationMs;

    @Value("${security.token.expiracy.access}")
    private long accessTokenExpirationMs;

    private final UserRepository userRepository;

    public JWTUtil(UserRepository userRepository,
            @Value("${security.token.secret.refresh}") String refreshSecret,
            @Value("${security.token.secret.access}") String accessSecret) {
        this.userRepository = userRepository;
        this.keyRefresh = secretKey(refreshSecret, "refresh");
        this.keyAccess = secretKey(accessSecret, "access");
    }

    private SecretKey secretKey(String secret, String purpose) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT " + purpose + " signing secret is required");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT " + purpose + " signing secret must be at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Generates a refresh token bound to a specific session.
     *
     * @param username  token subject
     * @param sessionId session identifier stored in the {@code sid} claim, used to
     *                  match the token against its persisted record on rotation
     * @return signed compact JWT string
     */
    public String generateRefreshToken(String username, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("sid", sessionId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpirationMs))
                .signWith(keyRefresh)
                .compact();
    }

    /** Generates a short-lived access token bound to the current login session. */
    public String generateAccessToken(String username, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("sid", sessionId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpirationMs))
                .signWith(keyAccess)
                .compact();
    }

    /**
     * Looks up the {@link User} entity for a username, or {@code null} if absent.
     */
    public User getUser(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * Extracts the subject (username) from a token.
     *
     * @param type 0 = access token, 1 = refresh token (selects the verification
     *             key)
     */
    public String extractUsername(int type, String token) {
        return parseClaims(type, token).getSubject();
    }

    /**
     * @param type  0 represents access code, 1 represents refresh code
     * @param token target token to be validated
     * @return true if the token is valid, false otherwise
     */
    public boolean isValid(int type, String token) {
        try {
            parseClaims(type, token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parses and verifies a token's signature, returning its claims.
     * Throws {@link JwtException} if the signature is invalid or the token expired.
     */
    private Claims parseClaims(int type, String token) {
        SecretKey key = (type == 0) ? keyAccess : keyRefresh;
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns the remaining validity time of a token in milliseconds
     * (never negative; 0 means already expired).
     */
    public long expirationRemaining(int type, String token) {
        Claims claims = parseClaims(type, token);
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        return Math.max(exp - now, 0);
    }

    /** Extracts the {@code sid} (session id) claim from a refresh token. */
    public String extractSessionId(String token) {
        return parseClaims(1, token).get("sid", String.class);
    }

    /** Extracts the session id using the key appropriate for the token type. */
    public String extractSessionId(int type, String token) {
        return parseClaims(type, token).get("sid", String.class);
    }

    public boolean isSessionCurrent(String username, String sid) {
        User user = getUser(username);
        if (user == null || sid == null || user.getCurrentSessionId() == null) {
            return false;
        }
        return sid.equals(user.getCurrentSessionId());
    }
}
