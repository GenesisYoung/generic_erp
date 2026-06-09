package com.gsgd.generic_erp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.user.User;
import com.gsgd.generic_erp.util.BasicResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JWTUtil jwtUtil;

    // Login endpoint
    @PostMapping("/login")
    public BasicResponse postMethodName(@RequestBody AuthenticationRequest entity) {
        try {
            Authentication auth = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(entity.username, entity.password));
            String refreshToken = JWTUtil.generateRefreshToken(auth.getName());
            String accessToken = JWTUtil.generateAccessToken(auth.getName());
            User user = jwtUtil.getUser(entity.username);
            UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
            return new BasicResponse(200, "Login successful",
                    new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
        } catch (Exception e) {
            return new BasicResponse(401, "Authentication failed", null);
        }
    }

    // Token expiration remaining endpoint
    @RequestMapping(path = "/expiration/remaining", method = RequestMethod.GET)
    public long expirationRemaining(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7);
        if (jwtUtil.isValid(refreshToken.trim())) {
            String token = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
            return JWTUtil.expirationRemaining(token.trim());
        } else {
            return -1; // Invalid token
        }
    }

    // Refresh access token endpoint
    @RequestMapping(path = "/refresh/access", method = RequestMethod.POST)
    public BasicResponse accessToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
        if (!jwtUtil.isValid(refreshToken.trim())) {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
        String username = jwtUtil.extractUsername(refreshToken.trim());
        if (username != null) {
            String newAccessToken = JWTUtil.generateAccessToken(username);
            return new BasicResponse(200, "Token refreshed successfully", newAccessToken);
        } else {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
    }

    // Refresh refresh token endpoint
    @RequestMapping(path = "/refresh/refresh", method = RequestMethod.POST)
    public BasicResponse refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
        if (!jwtUtil.isValid(refreshToken.trim())) {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
        String username = jwtUtil.extractUsername(refreshToken.trim());
        if (username != null) {
            String newRefreshToken = JWTUtil.generateRefreshToken(username);
            return new BasicResponse(200, "Token refreshed successfully", newRefreshToken);
        } else {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
    }

    public record TokenPair(String refreshToken, String accessToken) {
    }

    // Authentication request record
    public record AuthenticationRequest(String username, String password) {
    }

    public record AuthenticationResponse(TokenPair tokens, UserDTO user) {
    }
}
