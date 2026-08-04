package com.gsgd.generic_erp.controller.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.service.auth.AuthenticationService;
import com.gsgd.generic_erp.util.BasicResponse;

import jakarta.servlet.http.HttpServletRequest;

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

    private AuthenticationService service;

    public AuthenticationController(AuthenticationService service) {
        this.service = service;
    }

    /**
     * Authenticates with username/password; returns a token pair plus user info.
     */
    @PostMapping("/login")
    public BasicResponse login(@RequestBody AuthenticationRequest entity, HttpServletRequest request) {
        return service.handleLogin(entity, request);
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
    public BasicResponse accessToken(HttpServletRequest request) {
        return service.refreshAccessToken(request);
    }

    /** Rotates the refresh token itself, invalidating the previous one. */
    @RequestMapping(path = "/refresh/refresh", method = RequestMethod.POST)
    public BasicResponse refreshToken(HttpServletRequest request) {
        return service.refreshRefreshToken(request);
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
