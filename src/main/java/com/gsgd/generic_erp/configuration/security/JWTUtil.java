package com.gsgd.generic_erp.configuration.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.repository.auth.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JWTUtil {
    // Replace with a secure, sufficiently long random key in production
    private final static SecretKey key = new SecretKeySpec(
            System.getenv("AUTHENTICATION_SECRET_KEY") == null
                    ? "test-secret-key-for-junit-only-1234567890".getBytes(StandardCharsets.UTF_8)
                    : System.getenv("AUTHENTICATION_SECRET_KEY").getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    // Refresh token valid for 7 days
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 1000 * 60 * 60 * 24 * 7;
    // Access token valid for 15 mins
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 1000 * 60 * 15;

    private UserRepository userRepository;

    public JWTUtil(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Generate a JWT refresh token for the given username
    public static String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + REFRESH_TOKEN_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public static String generateAccessToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_TOKEN_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public User getUser(String username) {
        return userRepository.findByUsername(username).orElse(null); // Password is not stored in the token
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    // Check if the token is valid (signature is correct and not expired)
    public boolean isValid(String token) {
        try {
            parseClaims(token); // throws if signature is wrong or token expired
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // Parse the JWT token and return the claims
    private static Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Calculate the remaining time until the token expires (in milliseconds)
    public static long expirationRemaining(String token) {
        Claims claims = parseClaims(token);
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        return exp - now <= 0 ? 0 : exp - now;
    }

}
