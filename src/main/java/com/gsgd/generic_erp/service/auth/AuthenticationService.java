package com.gsgd.generic_erp.service.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationRequest;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationResponse;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.TokenPair;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.util.BasicResponse;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;

    private final JWTUtil jwtUtil;

    AuthenticationService(AuthenticationManager authenticationManager, JWTUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public BasicResponse handleLogin(AuthenticationRequest entity) {
        try {
            Authentication auth = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(entity.username(), entity.password()));
            String refreshToken = jwtUtil.generateRefreshToken(auth.getName());
            String accessToken = jwtUtil.generateAccessToken(auth.getName());
            User user = jwtUtil.getUser(entity.username());
            UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                    null, user.getStatus());
            return new BasicResponse(200, "Login successful",
                    new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
        } catch (Exception e) {
            return new BasicResponse(401, "Authentication failed", null);
        }
    }

    public long getExpirationRemaining(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7);
        if (jwtUtil.isValid(1, refreshToken.trim())) {
            String token = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
            return jwtUtil.expirationRemaining(1, token.trim());
        } else {
            return -1; // Invalid token
        }
    }

    public BasicResponse refreshAccessToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
        if (!jwtUtil.isValid(1, refreshToken.trim())) {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
        String username = jwtUtil.extractUsername(1, refreshToken.trim());
        if (username != null) {
            String newAccessToken = jwtUtil.generateAccessToken(username);
            return new BasicResponse(200, "Token refreshed successfully", newAccessToken);
        } else {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
    }

    public BasicResponse refreshRefreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
        if (!jwtUtil.isValid(1, refreshToken.trim())) {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
        String username = jwtUtil.extractUsername(1, refreshToken.trim());
        if (username != null) {
            String newRefreshToken = jwtUtil.generateRefreshToken(username);
            return new BasicResponse(200, "Token refreshed successfully", newRefreshToken);
        } else {
            return new BasicResponse(401, "Invalid refresh token", null);
        }
    }

}
