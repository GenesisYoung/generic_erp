package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.gsgd.generic_erp.configuration.security.impl.CustomizedUserDetailServiceImpl;

import jakarta.servlet.FilterChain;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesJwtSubjectAndIgnoresSpoofedUsernameHeader() throws Exception {
        JWTUtil jwt = mock(JWTUtil.class);
        CustomizedUserDetailServiceImpl users = mock(CustomizedUserDetailServiceImpl.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer signed-token");
        request.addHeader("User-Name", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        User alice = new User("alice", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(jwt.isValid(0, "signed-token")).thenReturn(true);
        when(jwt.extractUsername(0, "signed-token")).thenReturn("alice");
        when(jwt.extractSessionId(0, "signed-token")).thenReturn("session-1");
        when(jwt.isSessionCurrent("alice", "session-1")).thenReturn(true);
        when(users.loadUserByUsername("alice")).thenReturn(alice);

        filter.doFilter(request, response, chain);

        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(users).loadUserByUsername("alice");
        verify(users, never()).loadUserByUsername("admin");
        verify(chain).doFilter(request, response);
    }

    @Test
    void malformedAuthorizationHeaderFallsThroughSafely() throws Exception {
        JWTUtil jwt = mock(JWTUtil.class);
        CustomizedUserDetailServiceImpl users = mock(CustomizedUserDetailServiceImpl.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(jwt, never()).isValid(0, "x");
        verify(chain).doFilter(request, response);
    }
}
