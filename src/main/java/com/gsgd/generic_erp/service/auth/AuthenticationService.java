package com.gsgd.generic_erp.service.auth;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JWTUtil jwtUtil;

    public BasicResponse handleLogin(AuthenticationRequest entity) {
        try {
            Authentication auth = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(entity.username(), entity.password()));
            String refreshToken = JWTUtil.generateRefreshToken(auth.getName());
            String accessToken = JWTUtil.generateAccessToken(auth.getName());
            User user = jwtUtil.getUser(entity.username());
            UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
            return new BasicResponse(200, "Login successful",
                    new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
        } catch (Exception e) {
            return new BasicResponse(401, "Authentication failed", null);
        }
    }

    public long getExpirationRemaining(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization").substring(7);
        if (jwtUtil.isValid(refreshToken.trim())) {
            String token = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
            return JWTUtil.expirationRemaining(token.trim());
        } else {
            return -1; // Invalid token
        }
    }

    public BasicResponse refreshAccessToken(HttpServletRequest request) {
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

    public BasicResponse refreshRefreshToken(HttpServletRequest request) {
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

}
