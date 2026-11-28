package com.gsgd.generic_erp.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.gsgd.generic_erp.util.SimpleResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void unknownPathIs404InTheCallersLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/api/root/users");
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.PUT, "/api/api/root/users",
                "api/api/root/users");

        LocaleContextHolder.setLocale(Locale.US);
        ResponseEntity<SimpleResponse> en = handler.handleNotFound(e, request);
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(en.getBody().getCode()).isEqualTo(404);
        assertThat(en.getBody().getMessage())
                .isEqualTo("The requested API does not exist: /api/api/root/users");

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        ResponseEntity<SimpleResponse> zh = handler.handleNotFound(e, request);
        assertThat(zh.getBody().getMessage()).isEqualTo("请求的接口不存在：/api/api/root/users");
    }

    @Test
    void wrongMethodIs405() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/root/users");
        LocaleContextHolder.setLocale(Locale.US);
        ResponseEntity<SimpleResponse> resp = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().getMessage())
                .isEqualTo("This request method is not supported for this API: DELETE /root/users");
    }
}
