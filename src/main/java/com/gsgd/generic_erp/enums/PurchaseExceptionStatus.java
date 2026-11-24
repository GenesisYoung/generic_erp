package com.gsgd.generic_erp.enums;

/**
 * REPORTED -> ACKNOWLEDGED -> IN_PROGRESS -> PENDING_APPROVAL -> RESOLVED -> CLOSED.
 * <p>
 * A handler's resolution only proposes the fix: the exception waits in
 * PENDING_APPROVAL until the PMC department approves it (then RESOLVED) or
 * rejects it (back to IN_PROGRESS). A RESOLVED exception can be reopened to
 * IN_PROGRESS; resolving it again needs a new PMC approval.
 */
public enum PurchaseExceptionStatus {
    REPORTED, ACKNOWLEDGED, IN_PROGRESS, PENDING_APPROVAL, RESOLVED, CLOSED;

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }
}
