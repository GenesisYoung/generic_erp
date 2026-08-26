package com.gsgd.generic_erp.controller.auth;

import java.security.Principal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.service.auth.AuthenticationService;
import com.gsgd.generic_erp.util.BasicResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;

/**
 * Public authentication endpoints ({@code /api/auth}).
 * <p>
 * Handles login and both halves of the token-rotation flow: exchanging a
 * refresh token for a new access token, and rotating the refresh token itself.
 * These are the only endpoints (besides registration) permitted without a JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private static final String REFRESH_COOKIE = "erp_refresh";

    private AuthenticationService service;

    @Value("${security.token.cookie.secure:true}")
    private boolean secureCookie;

    public AuthenticationController(AuthenticationService service) {
        this.service = service;
    }

    /**
     * Authenticates with username/password; returns a token pair plus user info.
     */
    @PostMapping("/login")
    public BasicResponse login(@RequestBody AuthenticationRequest entity, HttpServletRequest request,
            HttpServletResponse response) {
        return moveRefreshTokenToCookie(service.handleLogin(entity, request), response);
    }

    // // Token expiration remaining endpoint
    // @RequestMapping(path = "/expiration/remaining", method = RequestMethod.GET)
    // public long expirationRemaining(HttpServletRequest request) {
    // return service.getExpirationRemaining(request);
    // }

    /**
     * Exchanges a valid refresh token (from the request header) for a new access
     * token.
     */
    @RequestMapping(path = "/refresh/access", method = RequestMethod.POST)
    public BasicResponse accessToken(HttpServletRequest request, HttpServletResponse response) {
        if (!"1".equals(request.getHeader("X-Refresh-Request"))) {
            return new BasicResponse(400, "Invalid refresh request", null);
        }
        return moveRefreshTokenToCookie(service.refreshAccessToken(refreshCookie(request)), response);
    }

    /** Rotates the refresh token itself, invalidating the previous one. */
    @RequestMapping(path = "/refresh/refresh", method = RequestMethod.POST)
    public BasicResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        if (!"1".equals(request.getHeader("X-Refresh-Request"))) {
            return new BasicResponse(400, "Invalid refresh request", null);
        }
        return moveRefreshTokenToCookie(service.refreshRefreshToken(refreshCookie(request)), response);
    }

    /** Revokes the current session and its refresh token. */
    @PostMapping("/logout")
    public BasicResponse logout(Principal principal, HttpServletResponse response) {
        clearRefreshCookie(response);
        return service.logout(principal.getName());
    }

    private BasicResponse moveRefreshTokenToCookie(BasicResponse result, HttpServletResponse response) {
        if (result.getStatus() != 200) {
            clearRefreshCookie(response);
            return result;
        }
        if (result.getObject() instanceof AuthenticationResponse authentication) {
            TokenPair pair = authentication.tokens();
            setRefreshCookie(response, pair.refreshToken());
            result.setObject(new AuthenticationResponse(new TokenPair(null, pair.accessToken()), authentication.user()));
        } else if (result.getObject() instanceof TokenPair pair) {
            setRefreshCookie(response, pair.refreshToken());
            result.setObject(new TokenPair(null, pair.accessToken()));
        }
        return result;
    }

    private String refreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Access + refresh token pair returned on login and rotation. */
    public record TokenPair(String refreshToken, String accessToken) {
    }

    /** Login request body. */
    public record AuthenticationRequest(String username, String password) {
    }

    /** Successful login payload: tokens plus the authenticated user's profile. */
    public record AuthenticationResponse(TokenPair tokens, UserDTO user) {
    }
}
