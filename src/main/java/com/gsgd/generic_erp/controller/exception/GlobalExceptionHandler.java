package com.gsgd.generic_erp.controller.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.util.SimpleResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<SimpleResponse> handleException(DataIntegrityViolationException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT) // 409
                .body(new SimpleResponse(409, e.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<SimpleResponse> handleNoneDeleteable(NoneDeleteableException e) {
        return ResponseEntity
                .status(HttpStatus.LOCKED) // 409
                .body(new SimpleResponse(423, e.getMessage()));
    }

    // Unexpected error Handler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SimpleResponse> handleUnexpected(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SimpleResponse(423, e.getMessage()));
    }

}
