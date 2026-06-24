package com.gsgd.generic_erp.configuration.security;

import java.io.IOException;

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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private JWTUtil jwtService;
    private CustomizedUserDetailServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JWTUtil jwtUtil, CustomizedUserDetailServiceImpl impl) {
        jwtService = jwtUtil;
        userDetailsService = impl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
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

        filterChain.doFilter(request, response);
    }

}
