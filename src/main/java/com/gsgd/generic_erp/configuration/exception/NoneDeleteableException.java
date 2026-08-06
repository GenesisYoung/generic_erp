package com.gsgd.generic_erp.configuration.exception;

public class NoneDeleteableException extends Exception {

    private String message;

    public NoneDeleteableException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

}
