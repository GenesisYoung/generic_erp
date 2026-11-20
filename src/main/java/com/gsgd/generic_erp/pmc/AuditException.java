package com.gsgd.generic_erp.pmc;

/**
 * A business error of the order audit module. {@code code} is a stable
 * machine-readable reason (tests and the frontend use it); the message is a
 * key in {@code i18n/messages*.properties}, rendered in the caller's language
 * by {@link AuditErrorHandler}. {@code args} fill the key's {0}, {1}, ...
 */
public class AuditException extends RuntimeException {
    public final int status;
    public final String code;
    public final transient Object[] args;

    public AuditException(int status, String code, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.args = args == null ? new Object[0] : args;
    }

    public static void require(boolean condition, int status, String code, String messageKey, Object... args) {
        if (!condition)
            throw new AuditException(status, code, messageKey, args);
    }
}
