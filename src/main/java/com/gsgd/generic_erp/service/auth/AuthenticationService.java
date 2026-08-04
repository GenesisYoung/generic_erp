package com.gsgd.generic_erp.service.auth;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
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
import com.gsgd.generic_erp.entity.auth.LoginLog;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.enums.Language_EN;
import com.gsgd.generic_erp.repository.auth.LoginLogRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalVariable;

import io.jsonwebtoken.lang.Objects;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Business logic for login and token rotation.
 * <p>
 * Responsibilities: credential verification through Spring Security's
 * {@link AuthenticationManager}, failed-attempt tracking with automatic
 * account locking (3 strikes), single-session enforcement via a per-login
 * session id embedded in the refresh token, login audit logging, and
 * localized (EN/CN) response messages.
 */
@Service
public class AuthenticationService {

        private final AuthenticationManager authenticationManager;

        private final JWTUtil jwtUtil;

        private final UserRepository userRepository;

        private final LoginLogRepository loginLogRepository;

        private final GlobalVariable globalVariable;

        private final RedisTemplate<String, Object> redisTemplateService;

        AuthenticationService(AuthenticationManager authenticationManager, JWTUtil jwtUtil,
                        UserRepository userRepository,
                        LoginLogRepository loginLogRepository,
                        GlobalVariable globalVariable,
                        RedisTemplate<String, Object> redisTemplateService)
                        throws ClassNotFoundException {
                this.authenticationManager = authenticationManager;
                this.jwtUtil = jwtUtil;
                this.userRepository = userRepository;
                this.loginLogRepository = loginLogRepository;
                this.globalVariable = globalVariable;
                this.redisTemplateService = redisTemplateService;
        }

        /**
         * Full login flow:
         * <ol>
         * <li>Reject unknown or disabled accounts.</li>
         * <li>Verify the password via the AuthenticationManager (Argon2).</li>
         * <li>Reset the failed-attempt counter on success; on a bad password,
         * increment it and disable the account after 3 failures.</li>
         * <li>Issue a new session id + token pair (invalidates older sessions,
         * since the refresh token's {@code sid} must match the stored one).</li>
         * <li>Record a login audit log entry.</li>
         * </ol>
         *
         * @return 200 with tokens and user info; 401/402 on failure
         */
        @SuppressWarnings("null")
        public BasicResponse handleLogin(AuthenticationRequest entity, HttpServletRequest request) {
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
                        // Store the refresh token in Redis with a TTL of 7 days, new login will
                        // overwrite the previous one, enforcing single-session.
                        redisTemplateService.opsForValue().set("refreshToken:" + user.getUsername(), refreshToken);
                        redisTemplateService.expire("refreshToken:" + user.getUsername(), java.time.Duration.ofDays(7));
                        LoginLog loginLog = LoginLog.builder()
                                        .userId(user.getId())
                                        .loginIp(getClientIp(request))
                                        .loginTime(LocalDateTime.now())
                                        .status(1)
                                        .createDate(new Date(new java.util.Date().getTime()))
                                        .build();
                        loginLogRepository.save(loginLog);
                        return new BasicResponse(200,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.LOGIN_SUCCESSFUL.getMessage()
                                                        : Language_CN.LOGIN_SUCCESSFUL.getMessage(),
                                        new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
                } catch (BadCredentialsException e) {
                        // Wrong password: increment the failed-attempt counter and lock the
                        // account (isEnabled = 0) once it reaches 3 consecutive failures.
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

        /**
         * Returns the remaining validity (ms) of the refresh token carried in the
         * {@code Authorization} header, or -1 if the token is invalid.
         */
        public long getExpirationRemaining(HttpServletRequest request) {
                String refreshToken = request.getHeader("Authorization").substring(7);
                if (jwtUtil.isValid(1, refreshToken.trim())) {
                        String token = request.getHeader("Authorization").substring(7); // Remove "Bearer " prefix
                        return jwtUtil.expirationRemaining(1, token.trim());
                } else {
                        return -1; // Invalid token
                }
        }

        /**
         * Issues a new access token in exchange for a valid refresh token.
         * The token's embedded session id must match the user's current session id —
         * this rejects refresh tokens from older logins (single-session enforcement).
         */
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

        /**
         * Rotates the refresh token: validates the presented one and issues a new
         * refresh token bound to the same session id.
         */
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

        public static String getClientIp(HttpServletRequest request) {
                if (request == null) {
                        return "unknown";
                }

                for (String header : IP_HEADERS) {
                        String ipList = request.getHeader(header);
                        if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
                                // X-Forwarded-For can contain a comma-separated list of proxy IPs.
                                // The first IP is generally the original client.
                                return ipList.split(",")[0].trim();
                        }
                }

                // Fallback to direct connection IP if no proxy headers are found
                return request.getRemoteAddr();
        }

        private static final String[] IP_HEADERS = {
                        "HTTP_CLIENT_IP",
                        "REMOTE_ADDR"
        };

}
