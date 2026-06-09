package com.gsgd.generic_erp.controller.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.service.auth.AuthenticationService;
import com.gsgd.generic_erp.util.BasicResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationService service;

    // Login endpoint
    @PostMapping("/login")
    public BasicResponse login(@RequestBody AuthenticationRequest entity) {
        return service.handleLogin(entity);
    }

    // Token expiration remaining endpoint
    @RequestMapping(path = "/expiration/remaining", method = RequestMethod.GET)
    public long expirationRemaining(HttpServletRequest request) {
        return service.getExpirationRemaining(request);
    }

    // Refresh access token endpoint
    @RequestMapping(path = "/refresh/access", method = RequestMethod.POST)
    public BasicResponse accessToken(HttpServletRequest request) {
        return service.refreshAccessToken(request);
    }

    // Refresh refresh token endpoint
    @RequestMapping(path = "/refresh/refresh", method = RequestMethod.POST)
    public BasicResponse refreshToken(HttpServletRequest request) {
        return service.refreshRefreshToken(request);
    }

    public record TokenPair(String refreshToken, String accessToken) {
    }

    // Authentication request record
    public record AuthenticationRequest(String username, String password) {
    }

    public record AuthenticationResponse(TokenPair tokens, UserDTO user) {
    }
}
