package com.gsgd.generic_erp.controller.exception;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.gsgd.generic_erp.configuration.exception.AuthenticationException;
import com.gsgd.generic_erp.configuration.exception.DuplicateValueException;
import com.gsgd.generic_erp.configuration.exception.FileOwnershipMismatchException;
import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 
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
                                .body(new SimpleResponse(NoneDeleteableException.code, I18n.t(e.getMessage())));
        }

        // A uniqueness check made in code: the message is already localized and safe.
        @ExceptionHandler(DuplicateValueException.class)
        public ResponseEntity<SimpleResponse> handleDuplicate(DuplicateValueException e) {
                log.warn("Duplicate value: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new SimpleResponse(409, e.getMessage()));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<SimpleResponse> handleIntegrity(DataIntegrityViolationException e) {
                log.warn("Constraint violation", e);
                // Never send the raw message — it exposes table and column names.
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new SimpleResponse(409, I18n.t("error.conflict")));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<SimpleResponse> handleInvalidInvalidRequest(IllegalArgumentException e) {
                log.warn("Invalid request: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new SimpleResponse(AuthenticationException.code, I18n.t(e.getMessage())));
        }

        // @PreAuthorize denials surface inside the MVC layer, so map them here;
        // otherwise the catch-all below would turn them into 500s.
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<SimpleResponse> handleAccessDenied(AccessDeniedException e,
                        HttpServletRequest request) {
                log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(new SimpleResponse(403, I18n.t("error.accessDenied")));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<SimpleResponse> handleInvalidRequest(IllegalArgumentException e) {
                log.warn("Invalid request: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new SimpleResponse(400, I18n.t(e.getMessage())));
        }

        // A URL that no controller maps (for example a wrong prefix such as /api/api/...).
        // Spring treats it as a missing static file; it is the caller's mistake, not a
        // server bug, so answer 404 instead of letting the catch-all below make it a 500.
        @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
        public ResponseEntity<SimpleResponse> handleNotFound(Exception e, HttpServletRequest request) {
                log.warn("No endpoint: {} {}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new SimpleResponse(404, I18n.t("error.notFound", request.getRequestURI())));
        }

        // The URL exists but not for this HTTP method (for example DELETE on a GET-only path).
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<SimpleResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                        HttpServletRequest request) {
                log.warn("Method not allowed: {} {}", request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(new SimpleResponse(405, I18n.t("error.methodNotAllowed",
                                                request.getMethod() + " " + request.getRequestURI())));
        }

        // Unexpected: a bug. ERROR level, full stack trace, generic message to the
        // client.
        @ExceptionHandler(Exception.class)
        public ResponseEntity<SimpleResponse> handleUnexpected(Exception e, HttpServletRequest request) {
                String traceId = UUID.randomUUID().toString().substring(0, 8);
                log.error("Unhandled exception [traceId={}] {} {}",
                                traceId, request.getMethod(), request.getRequestURI(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new SimpleResponse(500, I18n.t("error.internal", traceId) + "\n"
                                                + "Message===>" + e.getMessage()));
        }

        @ExceptionHandler(FileOwnershipMismatchException.class)
        public SimpleResponse handleFileManipulatedByUnMatchedUserError(FileOwnershipMismatchException e) {
                log.error(e.getMessage());
                return SimpleResponse.builder()
                                .code(e.getCode())
                                .message(I18n.t(e.getMessage()))
                                .build();
        }
}
