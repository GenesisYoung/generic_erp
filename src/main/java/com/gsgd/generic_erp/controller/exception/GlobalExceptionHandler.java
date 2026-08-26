package com.gsgd.generic_erp.controller.exception;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Central exception handler for the application.
 * Converts domain and framework exceptions into consistent HTTP responses
 * for API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Expected: the message is safe and useful for the user. WARN level, no stack
    // trace.
    @ExceptionHandler(NoneDeleteableException.class)
    public ResponseEntity<SimpleResponse> handleNoneDeleteable(NoneDeleteableException e) {
        log.warn("Delete rejected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new SimpleResponse(423, e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<SimpleResponse> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Constraint violation", e);
        // Never send the raw message — it exposes table and column names.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SimpleResponse(409, "This record conflicts with existing data"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SimpleResponse> handleInvalidRequest(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SimpleResponse(400, e.getMessage()));
    }

    // Unexpected: a bug. ERROR level, full stack trace, generic message to the
    // client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SimpleResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [traceId={}] {} {}",
                traceId, request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SimpleResponse(500, "Internal server error. Reference: " + traceId));
    }
}
