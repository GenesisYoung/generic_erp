package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.repository.auth.UserRepository;

class JWTUtilTest {

    private static final String ACCESS_SECRET = "test-access-secret-that-is-at-least-32-bytes";
    private static final String REFRESH_SECRET = "test-refresh-secret-that-is-at-least-32-bytes";

    @Test
    void accessTokenCarriesSignedIdentityAndSession() {
        JWTUtil jwt = new JWTUtil(mock(UserRepository.class), REFRESH_SECRET, ACCESS_SECRET);
        setExpirations(jwt);

        String token = jwt.generateAccessToken("alice", "session-1");

        assertTrue(jwt.isValid(0, token));
        assertEquals("alice", jwt.extractUsername(0, token));
        assertEquals("session-1", jwt.extractSessionId(0, token));
        assertFalse(jwt.isValid(1, token));
    }

    @Test
    void malformedTokensAreRejectedWithoutThrowing() {
        JWTUtil jwt = new JWTUtil(mock(UserRepository.class), REFRESH_SECRET, ACCESS_SECRET);
        assertFalse(jwt.isValid(0, "not-a-jwt"));
        assertFalse(jwt.isValid(0, ""));
    }

    @Test
    void weakSigningSecretsFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new JWTUtil(mock(UserRepository.class), "short", ACCESS_SECRET));
    }

    private void setExpirations(JWTUtil jwt) {
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "accessTokenExpirationMs", 60_000L);
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "refreshTokenExpirationMs", 60_000L);
    }
}
