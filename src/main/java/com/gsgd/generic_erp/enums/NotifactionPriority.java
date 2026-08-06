package com.gsgd.generic_erp.enums;

public enum NotifactionPriority {
    ERGENT(3),
    HIGH(2),
    NORMAL(1);

    int value;

    NotifactionPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
