package com.gsgd.generic_erp.enums;

/**
 * REPORTED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> CLOSED. A RESOLVED
 * exception can be reopened to IN_PROGRESS.
 */
public enum PurchaseExceptionStatus {
    REPORTED, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED;

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }
}
