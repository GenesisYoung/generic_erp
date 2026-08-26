package com.gsgd.generic_erp.enums;

public enum StatusCommand {
    LOGOUT(101),
    SESSION_SUPERSEDED(1);

    int code;

    StatusCommand(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
