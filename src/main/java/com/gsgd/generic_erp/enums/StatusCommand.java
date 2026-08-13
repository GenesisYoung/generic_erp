package com.gsgd.generic_erp.enums;

public enum StatusCommand {
    LOGOUT(101);

    int code;

    StatusCommand(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
