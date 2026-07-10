package com.gsgd.generic_erp.service.auth;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationRequest;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationResponse;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.TokenPair;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.enums.Language_EN;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalVariable;

import io.jsonwebtoken.lang.Objects;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthenticationService {

        private final AuthenticationManager authenticationManager;

        private final JWTUtil jwtUtil;

        private final UserRepository userRepository;

        private final GlobalVariable globalVariable;

        AuthenticationService(AuthenticationManager authenticationManager, JWTUtil jwtUtil,
                        UserRepository userRepository,
                        GlobalVariable globalVariable)
                        throws ClassNotFoundException {
                this.authenticationManager = authenticationManager;
                this.jwtUtil = jwtUtil;
                this.userRepository = userRepository;
                this.globalVariable = globalVariable;

        }

        public BasicResponse handleLogin(AuthenticationRequest entity) {
                try {
                        String username = entity.username().trim();
                        User exist = userRepository.findByUsername(username)
                                        .filter(user -> user.getIsEnabled() != null && user.getIsEnabled() == 1)
                                        .orElseThrow(() -> new RuntimeException(
                                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                        ? Language_EN.USER_NOT_AVAILABLE.getMessage()
                                                                        : Language_CN.USER_NOT_AVAILABLE.getMessage()));
                        if (Objects.isEmpty(exist)) {
                                return new BasicResponse(402,
                                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                ? Language_EN.USER_NOT_AVAILABLE.getMessage()
                                                                : Language_CN.USER_NOT_AVAILABLE.getMessage(),
                                                null);
                        }
                        Authentication auth = authenticationManager
                                        .authenticate(new UsernamePasswordAuthenticationToken(entity.username(),
                                                        entity.password()));
                        if (auth.isAuthenticated() && exist.getFailedAttempted() != null
                                        && exist.getFailedAttempted() > 0) {
                                exist.setFailedAttempted((byte) 0);
                                userRepository.save(exist);
                        } else if (exist.getFailedAttempted() != null && exist.getFailedAttempted() >= 3) {
                                return new BasicResponse(401,
                                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                ? Language_EN.INVALID_CREDENTIALS.getMessage()
                                                                : Language_CN.INVALID_CREDENTIALS.getMessage(),
                                                null);
                        }
                        User user = jwtUtil.getUser(entity.username());
                        String sessionId = UUID.randomUUID().toString();
                        user.setCurrentSessionId(sessionId);
                        userRepository.save(user);
                        String refreshToken = jwtUtil.generateRefreshToken(auth.getName(), user.getCurrentSessionId());
                        String accessToken = jwtUtil.generateAccessToken(auth.getName());
                        UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(),
                                        user.getDisplayName(),
                                        null, user.getStatus(), user.getIsEnabled());
                        return new BasicResponse(200,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.LOGIN_SUCCESSFUL.getMessage()
                                                        : Language_CN.LOGIN_SUCCESSFUL.getMessage(),
                                        new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
                } catch (BadCredentialsException e) {
                        String username = entity.username().trim();
                        User user = userRepository.findByUsername(username)
                                        .filter(u -> u.getIsEnabled() != null && u.getIsEnabled() == 1)
                                        .orElseThrow(() -> new RuntimeException(
                                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                        ? Language_EN.USER_NOT_AVAILABLE.getMessage()
                                                                        : Language_CN.USER_NOT_AVAILABLE.getMessage()));
                        byte failedAttempts = user.getFailedAttempted() != null ? user.getFailedAttempted() : 0;
                        user.setFailedAttempted(failedAttempts < 3 ? (byte) (failedAttempts + 1) : failedAttempts);
                        userRepository.save(user);
                        if (failedAttempts + 1 >= 3) {
                                user.setIsEnabled((byte) 0); // Disable the user account
                                userRepository.save(user);
                                return new BasicResponse(402,
                                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                ? Language_EN.MULTIPLE_FAILURE.getMessage()
                                                                : Language_CN.MULTIPLE_FAILURE.getMessage(),
                                                null);
                        }
                        return new BasicResponse(401,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.INVALID_CREDENTIALS.getMessage()
                                                        : Language_CN.INVALID_CREDENTIALS.getMessage(),
                                        null);
                } catch (RuntimeException e) {
                        return new BasicResponse(402, e.getMessage(), null);
                } catch (Exception e) {
                        return new BasicResponse(500,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.INTERNAL_SERVER_ERROR.getMessage()
                                                        : Language_CN.INTERNAL_SERVER_ERROR.getMessage(),
                                        null);
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
                        return new BasicResponse(401,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                        null);
                }
                String username = jwtUtil.extractUsername(1, refreshToken.trim());
                if (username != null) {
                        User u = jwtUtil.getUser(username);
                        if (u != null && !u.getCurrentSessionId()
                                        .equals(jwtUtil.extractSessionId(refreshToken.trim()))) {
                                return new BasicResponse(401,
                                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                                : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                                null);
                        } else if (u == null || u.getCurrentSessionId() == null) {
                                return new BasicResponse(401,
                                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                                ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                                : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                                null);
                        }
                        String newAccessToken = jwtUtil.generateAccessToken(username);
                        return new BasicResponse(200,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_SUCCESSFUL.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_SUCCESSFUL.getMessage(),
                                        newAccessToken);
                } else {
                        return new BasicResponse(401,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                        null);
                }
        }

        public BasicResponse refreshRefreshToken(HttpServletRequest request) {
                String refreshToken = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
                if (!jwtUtil.isValid(1, refreshToken.trim())) {
                        return new BasicResponse(401,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                        null);
                }
                String username = jwtUtil.extractUsername(1, refreshToken.trim());
                if (username != null) {
                        User u = jwtUtil.getUser(username);
                        String newRefreshToken = jwtUtil.generateRefreshToken(username, u.getCurrentSessionId());
                        return new BasicResponse(200,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_SUCCESSFUL.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_SUCCESSFUL.getMessage(),
                                        newRefreshToken);
                } else {
                        return new BasicResponse(401,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                        : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                        null);
                }
        }

}
