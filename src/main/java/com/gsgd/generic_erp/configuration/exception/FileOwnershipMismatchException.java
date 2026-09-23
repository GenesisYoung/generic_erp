package com.gsgd.generic_erp.configuration.exception;

public class FileOwnershipMismatchException extends RuntimeException {
    private String message;
    private final static int code = 424;

    public FileOwnershipMismatchException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

}
