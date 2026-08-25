package com.gsgd.generic_erp.controller.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationRequest;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.AuthenticationResponse;
import com.gsgd.generic_erp.controller.auth.AuthenticationController.TokenPair;
import com.gsgd.generic_erp.dto.UserDTO;
import com.gsgd.generic_erp.service.auth.AuthenticationService;
import com.gsgd.generic_erp.util.BasicResponse;

class AuthenticationControllerTest {

    @Test
    void loginMovesRefreshTokenIntoHttpOnlyCookie() {
        AuthenticationService service = mock(AuthenticationService.class);
        AuthenticationController controller = new AuthenticationController(service);
        ReflectionTestUtils.setField(controller, "secureCookie", false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationRequest credentials = new AuthenticationRequest("alice", "password");
        AuthenticationResponse payload = new AuthenticationResponse(
                new TokenPair("secret-refresh", "short-access"), new UserDTO());
        when(service.handleLogin(eq(credentials), any())).thenReturn(new BasicResponse(200, "ok", payload));

        BasicResponse result = controller.login(credentials, request, response);

        AuthenticationResponse publicPayload = (AuthenticationResponse) result.getObject();
        assertNotNull(publicPayload);
        assertTrue(publicPayload.tokens().refreshToken() == null);
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("erp_refresh=secret-refresh;"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }
}
