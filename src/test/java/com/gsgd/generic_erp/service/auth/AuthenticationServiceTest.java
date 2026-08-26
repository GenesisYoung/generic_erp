package com.gsgd.generic_erp.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gsgd.generic_erp.configuration.message.NotificationService;
import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.configuration.security.RefreshTokenStore;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.TokenPair;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.repository.auth.LoginLogRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserInfoRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalVariable;

class AuthenticationServiceTest {

    private JWTUtil jwt;
    private UserRepository users;
    private RefreshTokenStore tokens;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        jwt = mock(JWTUtil.class);
        users = mock(UserRepository.class);
        tokens = mock(RefreshTokenStore.class);
        GlobalVariable language = mock(GlobalVariable.class);
        when(language.getDEFAULT_LANGUAGE()).thenReturn("EN");
        service = new AuthenticationService(
                mock(AuthenticationManager.class),
                jwt,
                users,
                mock(LoginLogRepository.class),
                language,
                tokens,
                mock(UserInfoRepository.class),
                mock(UserDepartmentRepository.class),
                mock(NotificationService.class),
                mock(PasswordEncoder.class));
    }

    @Test
    void refreshTokenIsConsumedAndRotated() {
        User user = activeUser();
        MockHttpServletRequest request = refreshRequest("old-refresh");
        when(jwt.isValid(1, "old-refresh")).thenReturn(true);
        when(jwt.extractUsername(1, "old-refresh")).thenReturn("alice");
        when(jwt.extractSessionId("old-refresh")).thenReturn("session-1");
        when(jwt.getUser("alice")).thenReturn(user);
        when(jwt.generateRefreshToken("alice", "session-1")).thenReturn("new-refresh");
        when(tokens.rotate("alice", "old-refresh", "new-refresh")).thenReturn(true);
        when(jwt.generateAccessToken("alice", "session-1")).thenReturn("new-access");

        BasicResponse response = service.refreshAccessToken(request);

        assertEquals(200, response.getStatus());
        assertEquals(new TokenPair("new-refresh", "new-access"), response.getObject());
        verify(tokens).rotate("alice", "old-refresh", "new-refresh");
    }

    @Test
    void replayedRefreshTokenRevokesTheSession() {
        User user = activeUser();
        MockHttpServletRequest request = refreshRequest("replayed-refresh");
        when(jwt.isValid(1, "replayed-refresh")).thenReturn(true);
        when(jwt.extractUsername(1, "replayed-refresh")).thenReturn("alice");
        when(jwt.extractSessionId("replayed-refresh")).thenReturn("session-1");
        when(jwt.getUser("alice")).thenReturn(user);
        when(jwt.generateRefreshToken("alice", "session-1")).thenReturn("unused-refresh");
        when(tokens.rotate("alice", "replayed-refresh", "unused-refresh")).thenReturn(false);

        BasicResponse response = service.refreshAccessToken(request);

        assertEquals(401, response.getStatus());
        assertNull(user.getCurrentSessionId());
        verify(tokens).revoke("alice");
        verify(users).save(user);
    }

    private User activeUser() {
        User user = new User();
        user.setUsername("alice");
        user.setCurrentSessionId("session-1");
        user.setIsEnabled((byte) 1);
        return user;
    }

    private MockHttpServletRequest refreshRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh/access");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
