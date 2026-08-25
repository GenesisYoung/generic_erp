package com.gsgd.generic_erp.configuration.security;

import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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
    public JwtAuthenticationFilter(JWTUtil jwtUtil, CustomizedUserDetailServiceImpl impl) {
        this.jwtService = jwtUtil;
        this.userDetailsService = impl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty() || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.isValid(0, token)) {
                // The signed JWT subject is the only source of identity. Never trust a
                // client-supplied username header for authentication.
                String username = jwtService.extractUsername(0, token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                String sessionId = jwtService.extractSessionId(0, token);
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()
                        && jwtService.isSessionCurrent(username, sessionId)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (UsernameNotFoundException ignored) {
            // A token for a deleted account must remain unauthenticated.
        }

        filterChain.doFilter(request, response);
    }

}
