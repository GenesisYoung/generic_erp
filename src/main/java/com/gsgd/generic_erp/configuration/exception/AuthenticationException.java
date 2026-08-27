package com.gsgd.generic_erp.configuration.exception;

public class AuthenticationException extends Exception {

    private String message;

    public final static int code = 401;

    public AuthenticationException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    public int getCode() {
        return code;
    }

}