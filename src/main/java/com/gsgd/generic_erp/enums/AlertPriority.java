package com.gsgd.generic_erp.enums;

public enum AlertPriority {
    INFO("info"),
    WARNING("warning"),
    ERROR("error");

    String value;

    AlertPriority(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
