package com.gsgd.generic_erp.configuration.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JWTUtil {
        // Replace with a secure, sufficiently long random key in production
        private final static SecretKey key = new SecretKeySpec(
            System.getenv("AUTHENTICATION_SECRET_KEY")==null?"test-secret-key-for-junit-only-1234567890".getBytes(StandardCharsets.UTF_8):System.getenv("AUTHENTICATION_SECRET_KEY").getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
        private static final long EXPIRATION_MS = 1000 * 60 * 60 * 24 * 7;
        
        public static String generateToken(String username) {
            long now = System.currentTimeMillis();
            return Jwts.builder()
            .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRATION_MS))
                .signWith(key)
                .compact();
        }

        public String extractUsername(String token) {
            return parseClaims(token).getSubject();
        }

        public boolean isValid(String token) {
        try {
            parseClaims(token); // throws if signature is wrong or token expired
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

        private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

}
