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

@Service
public class JWTUtil {

    private final SecretKey keyRefresh;
    private final SecretKey keyAccess;

    @Value("${security.token.expiracy.refresh}")
    private long refreshTokenExpirationMs;

    @Value("${security.token.expiracy.access}")
    private long accessTokenExpirationMs;

    private final UserRepository userRepository;

    public JWTUtil(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.keyRefresh = new SecretKeySpec(
                System.getenv("AUTHENTICATION_SECRET_KEY_REFRESH") == null
                        ? "alternative_key_for_refresh_token_732dwddw32cioniso1".getBytes(StandardCharsets.UTF_8)
                        : System.getenv("AUTHENTICATION_SECRET_KEY_REFRESH").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        this.keyAccess = new SecretKeySpec(
                System.getenv("AUTHENTICATION_SECRET_KEY_ACCESS") == null
                        ? "alternative_key_for_access_token_21kj414k1flscbh".getBytes(StandardCharsets.UTF_8)
                        : System.getenv("AUTHENTICATION_SECRET_KEY_ACCESS").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpirationMs))
                .signWith(keyRefresh)
                .compact();
    }

    public String generateAccessToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpirationMs))
                .signWith(keyAccess)
                .compact();
    }

    public User getUser(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

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
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims parseClaims(int type, String token) {
        SecretKey key = (type == 0) ? keyAccess : keyRefresh;
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    public long expirationRemaining(int type, String token) {
        Claims claims = parseClaims(type, token);
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        return Math.max(exp - now, 0);
    }
}