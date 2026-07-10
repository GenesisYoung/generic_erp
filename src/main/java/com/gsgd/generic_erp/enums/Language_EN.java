package com.gsgd.generic_erp.enums;

public enum Language_EN implements Language {

    MULTIPLE_FAILURE("Multiple failures, please try again later"),
    INVALID_CREDENTIALS("Invalid username or password"),
    USER_DISABLED("User is disabled"),
    USER_NOT_AVAILABLE("User not available!"),
    LOGIN_SUCCESSFUL("Login successful"),
    TOKEN_REFRESH_SUCCESSFUL("Token refresh successful"),
    TOKEN_REFRESH_FAILED("Token refresh failed"),
    TOKEN_EXPIRED("Token expired"),
    INTERNAL_SERVER_ERROR("Internal server error"),
    SEEESION_EXPIRED("Session expired, please login again");

    private final String value;

    Language_EN(String val) {
        this.value = val;
    }

    @Override
    public String getMessage() {
        return this.value;
    }
}