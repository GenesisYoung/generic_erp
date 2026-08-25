package com.gsgd.generic_erp.service.auth;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.message.NotificationService;
import com.gsgd.generic_erp.configuration.security.JWTUtil;
import com.gsgd.generic_erp.configuration.security.RefreshTokenStore;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationRequest;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationResponse;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.TokenPair;
import com.gsgd.generic_erp.dto.StatusNotificationDTO;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.entity.auth.LoginLog;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.auth.UserInfo;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.enums.Language_EN;
import com.gsgd.generic_erp.repository.auth.LoginLogRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.UserInfoRepository;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalVariable;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class AuthenticationService {

        private final AuthenticationManager authenticationManager;

        private final JWTUtil jwtUtil;

        private final UserRepository userRepository;

        private final LoginLogRepository loginLogRepository;

        private final GlobalVariable globalVariable;

        private final RefreshTokenStore refreshTokenStore;

        private final UserInfoRepository infoRepository;

        private final UserDepartmentRepository drepository;

        private final NotificationService messager;

        @Value("${security.login.max-failed-attempts:3}")
        private int maxFailedAttempts;

        @Value("${security.login.lock-duration-ms:900000}")
        private long lockDurationMs;

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
                String username = entity != null && entity.username() != null ? entity.username().trim() : "";
                if (username.isEmpty() || entity.password() == null || entity.password().isEmpty()) {
                        return invalidCredentials();
                }
                try {
                        Optional<User> existingUser = userRepository.findByUsername(username);
                        if (existingUser.isEmpty() || existingUser.get().getIsEnabled() == null
                                        || existingUser.get().getIsEnabled() != 1) {
                                return invalidCredentials();
                        }
                        User exist = existingUser.get();
                        if (exist.getLockedUntil() != null && exist.getLockedUntil().isAfter(LocalDateTime.now())) {
                                return invalidCredentials();
                        }
                        if (exist.getLockedUntil() != null) {
                                exist.setLockedUntil(null);
                                exist.setFailedAttempted((byte) 0);
                                userRepository.save(exist);
                        }
                        Authentication auth = authenticationManager
                                        .authenticate(new UsernamePasswordAuthenticationToken(username,
                                                        entity.password()));
                        if (auth.isAuthenticated() && exist.getFailedAttempted() != null
                                        && exist.getFailedAttempted() > 0) {
                                exist.setFailedAttempted((byte) 0);
                                userRepository.save(exist);
                        }
                        User user = jwtUtil.getUser(username);
                        String sessionId = UUID.randomUUID().toString();
                        user.setCurrentSessionId(sessionId);
                        userRepository.save(user);
                        String refreshToken = jwtUtil.generateRefreshToken(auth.getName(), user.getCurrentSessionId());
                        String accessToken = jwtUtil.generateAccessToken(auth.getName(), user.getCurrentSessionId());
                        Optional<UserInfo> op = infoRepository.findByUserId(user.getId());
                        UserDTO userDTO = null;
                        if (!op.isEmpty()) {
                                UserInfo info = infoRepository.findByUserId(user.getId()).get();
                                List<Long> departments = drepository.findByUserId(user.getId()).stream()
                                                .map(ele -> ele.getDeptId()).toList();
                                userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(),
                                                user.getDisplayName(),
                                                null, user.getStatus(), user.getIsEnabled(), null,
                                                info != null ? info.getRealName() : null,
                                                info != null ? info.getTitle() : null,
                                                info != null ? info.getBirthday() : null,
                                                info != null ? info.getHireDate() : null,
                                                info != null ? departments : null,
                                                null);
                        } else {
                                List<Long> departments = drepository.findByUserId(user.getId()).stream()
                                                .map(ele -> ele.getDeptId()).toList();
                                userDTO = new UserDTO(user.getId(), user.getUsername(), user.getEmail(),
                                                user.getDisplayName(),
                                                null, user.getStatus(), user.getIsEnabled(), null, "",
                                                "", null, null,
                                                departments,
                                                null);
                        }
                        // Store the refresh token in Redis with a TTL of 7 days, new login will
                        // overwrite the previous one, enforcing single-session.
                        refreshTokenStore.store(user.getUsername(), refreshToken);
                        LoginLog loginLog = LoginLog.builder()
                                        .userId(user.getId())
                                        .loginIp(getClientIp(request))
                                        .loginTime(LocalDateTime.now())
                                        .status(1)
                                        .createDate(new java.sql.Date(new java.util.Date().getTime()))
                                        .build();
                        loginLogRepository.save(loginLog);
                        /**
                         * Logout the former session
                         */
                        messager.statusUpdates(username, StatusNotificationDTO.builder().code(1)
                                        .object(new LogoutMessage(accessToken)).build());
                        return new BasicResponse(200,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.LOGIN_SUCCESSFUL.getMessage()
                                                        : Language_CN.LOGIN_SUCCESSFUL.getMessage(),
                                        new AuthenticationResponse(new TokenPair(refreshToken, accessToken), userDTO));
                } catch (BadCredentialsException e) {
                        userRepository.findByUsername(username)
                                        .filter(u -> u.getIsEnabled() != null && u.getIsEnabled() == 1)
                                        .ifPresent(user -> {
                                                int failedAttempts = user.getFailedAttempted() == null
                                                                ? 0
                                                                : user.getFailedAttempted();
                                                int nextAttempt = failedAttempts + 1;
                                                user.setFailedAttempted((byte) Math.min(nextAttempt, maxFailedAttempts));
                                                if (nextAttempt >= maxFailedAttempts) {
                                                        user.setLockedUntil(LocalDateTime.now()
                                                                        .plus(Duration.ofMillis(lockDurationMs)));
                                                }
                                                userRepository.save(user);
                                        });
                        return invalidCredentials();
                } catch (AuthenticationException e) {
                        return invalidCredentials();
                } catch (Exception e) {
                        return new BasicResponse(500,
                                        globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                        ? Language_EN.INTERNAL_SERVER_ERROR.getMessage()
                                                        : Language_CN.INTERNAL_SERVER_ERROR.getMessage(),
                                        null);
                }
        }

        private BasicResponse invalidCredentials() {
                return new BasicResponse(401,
                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                ? Language_EN.INVALID_CREDENTIALS.getMessage()
                                                : Language_CN.INVALID_CREDENTIALS.getMessage(),
                                null);
        }

        /**
         * Returns the remaining validity (ms) of the refresh token carried in the
         * {@code Authorization} header, or -1 if the token is invalid.
         */
        public long getExpirationRemaining(HttpServletRequest request) {
                String refreshToken = bearerToken(request);
                return refreshToken != null && jwtUtil.isValid(1, refreshToken)
                                ? jwtUtil.expirationRemaining(1, refreshToken)
                                : -1;
        }

        /**
         * Issues a new access token in exchange for a valid refresh token.
         * The token's embedded session id must match the user's current session id —
         * this rejects refresh tokens from older logins (single-session enforcement).
         */
        public BasicResponse refreshAccessToken(HttpServletRequest request) {
                return rotateTokenPair(request);
        }

        public BasicResponse refreshAccessToken(String refreshToken) {
                return rotateTokenPair(refreshToken);
        }

        /**
         * Rotates the refresh token: validates the presented one and issues a new
         * refresh token bound to the same session id.
         */
        public BasicResponse refreshRefreshToken(HttpServletRequest request) {
                return rotateTokenPair(request);
        }

        public BasicResponse refreshRefreshToken(String refreshToken) {
                return rotateTokenPair(refreshToken);
        }

        private BasicResponse rotateTokenPair(HttpServletRequest request) {
                return rotateTokenPair(bearerToken(request));
        }

        private BasicResponse rotateTokenPair(String refreshToken) {
                if (refreshToken == null || !jwtUtil.isValid(1, refreshToken)) {
                        return refreshFailure();
                }

                String username = jwtUtil.extractUsername(1, refreshToken);
                User user = jwtUtil.getUser(username);
                String sid = jwtUtil.extractSessionId(refreshToken);
                if (user == null || user.getCurrentSessionId() == null
                                || !user.getCurrentSessionId().equals(sid)
                                || user.getIsEnabled() == null || user.getIsEnabled() != 1) {
                        return refreshFailure();
                }

                String newRefreshToken = jwtUtil.generateRefreshToken(username, sid);
                if (!refreshTokenStore.rotate(username, refreshToken, newRefreshToken)) {
                        // A mismatch means this token was already rotated or revoked. Revoke the
                        // whole session to prevent a replay race from leaving either caller active.
                        refreshTokenStore.revoke(username);
                        user.setCurrentSessionId(null);
                        userRepository.save(user);
                        return refreshFailure();
                }

                String newAccessToken = jwtUtil.generateAccessToken(username, sid);
                return new BasicResponse(200,
                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                ? Language_EN.TOKEN_REFRESH_SUCCESSFUL.getMessage()
                                                : Language_CN.TOKEN_REFRESH_SUCCESSFUL.getMessage(),
                                new TokenPair(newRefreshToken, newAccessToken));
        }

        private String bearerToken(HttpServletRequest request) {
                String header = request.getHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                        return null;
                }
                String token = header.substring(7).trim();
                return token.isEmpty() ? null : token;
        }

        private BasicResponse refreshFailure() {
                return new BasicResponse(401,
                                globalVariable.getDEFAULT_LANGUAGE().equals("EN")
                                                ? Language_EN.TOKEN_REFRESH_FAILED.getMessage()
                                                : Language_CN.TOKEN_REFRESH_FAILED.getMessage(),
                                null);
        }

        public BasicResponse logout(String username) {
                refreshTokenStore.revoke(username);
                User user = jwtUtil.getUser(username);
                if (user != null) {
                        user.setCurrentSessionId(null);
                        userRepository.save(user);
                }
                return new BasicResponse(200, "Logged out", null);
        }

        public static String getClientIp(HttpServletRequest request) {
                // if (request == null) {
                // return "unknown";
                // }

                // for (String header : IP_HEADERS) {
                // String ipList = request.getHeader(header);
                // if (ipList != null && !ipList.isEmpty() &&
                // !"unknown".equalsIgnoreCase(ipList)) {
                // // X-Forwarded-For can contain a comma-separated list of proxy IPs.
                // // The first IP is generally the original client.
                // return ipList.split(",")[0].trim();
                // }
                // }

                // // Fallback to direct connection IP if no proxy headers are found
                // return request.getRemoteAddr();

                // Check standard proxy header used by most load balancers
                String ip = request.getHeader("X-Forwarded-For");

                // Check alternative proxy headers if the first one is empty
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("WL-Proxy-Client-IP"); // WebLogic
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("HTTP_CLIENT_IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("X-Real-IP"); // Nginx alternative
                }

                // If no proxies are found, grab the direct connection IP
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getRemoteAddr();
                }

                // X-Forwarded-For can contain a comma-separated list of multiple proxy IPs.
                // The first IP in the list is always the original client.
                if (ip != null && ip.contains(",")) {
                        ip = ip.split(",")[0].trim();
                }

                return ip;
        }

        private static final String[] IP_HEADERS = {
                        "HTTP_CLIENT_IP",
                        "REMOTE_ADDR"
        };

        record LogoutMessage(String latestAccessToken) {
        }

}
