package com.gsgd.generic_erp.annotations;

public enum AuditAction {
    CREATEORUPDATE("CREATE OR UPDATE"), DELETE("DELETE"), STATUS_CHANGE("STATUS_CHANGE");

    private String value;

    AuditAction(String val) {
        this.value = val;
    }

    public String getVal() {
        return value;
    }
}
