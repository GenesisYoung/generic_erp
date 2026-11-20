package com.gsgd.generic_erp.pmc;

public class AuditException extends RuntimeException {
    public final int status;
    public final String code;

    public AuditException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static void require(boolean condition, int status, String code, String message) {
        if (!condition)
            throw new AuditException(status, code, message);
    }
}
