package com.gsgd.generic_erp.configuration.security;

import java.io.IOException;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gsgd.generic_erp.configuration.security.impl.CustomizedUserDetailServiceImpl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that authenticates every request from its JWT.
 * <p>
 * Runs once per request, before Spring Security's username/password filter.
 * If a valid {@code Authorization: Bearer} access token is present, the
 * corresponding user is loaded and placed into the
 * {@link SecurityContextHolder}.
 * It also reports the remaining lifetime of the client's refresh token via the
 * {@code Refresh-Token-Remaining} response header so the frontend can decide
 * when to rotate it.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JWTUtil jwtService;
    private final CustomizedUserDetailServiceImpl userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;

    public JwtAuthenticationFilter(JWTUtil jwtUtil, CustomizedUserDetailServiceImpl impl,
            RedisTemplate<String, Object> redisTemplate) {
        this.jwtService = jwtUtil;
        this.userDetailsService = impl;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String refreshToken = request.getHeader("Refresh-Token");
        List<String> allowedEndpoints = List.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh/access",
                "/api/auth/refresh/refresh");
        String requestPath = request.getRequestURI();
        // If the request is for a public endpoint, skip token validation.
        if (!allowedEndpoints.contains(requestPath)) {
            String storedRefreshToken = (String) redisTemplate.opsForValue()
                    .get("refreshToken:" + jwtService.extractUsername(1, refreshToken));
            // If the refresh token in the request does not match the one stored in Redis,
            // reject the request.
            if (storedRefreshToken != null && refreshToken != null && !refreshToken.equals(storedRefreshToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid refresh token");
                return;
            }
        }
        // No token? Just continue; the AuthorizationFilter will reject it later if
        // needed.
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7); // strip "Bearer "
        // If the token is valid, extract the username and load user details,
        // then set the authentication in the security context.
        if (jwtService.isValid(0, token)) {
            String username = jwtService.extractUsername(0, token);
            // Load user details from the database using the username extracted from the
            // token.
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            // Create an authentication token with the user details and set it in the
            // security context.
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        long tokenRemainingTime = jwtService.expirationRemaining(1, refreshToken);
        response.addHeader("Refresh-Token-Remaining",
                String.valueOf(tokenRemainingTime));
        filterChain.doFilter(request, response);
    }

}
