package com.gsgd.generic_erp.enums;

/** English user-facing messages for auth and error responses. */
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
    SEEESION_EXPIRED("Session expired, please login again"),
    NAME_DULICATED("Exception, name duplicated!"),
    USERNAME_DULICATED("Exception, username duplicated!"),
    EMAIL_DULICATED("Exception, email duplicated!"),
    CANT_DELETE_ROOT_USER("Can't delete root user"),
    ROLE_ASSIGNED("This role is still assigned to users and cannot be deleted"),
    DEPT_ASSIGNED("This department is still assigned to users and cannot be deleted");

    private final String value;

    Language_EN(String val) {
        this.value = val;
    }

    @Override
    public String getMessage() {
        return this.value;
    }
}