package com.gsgd.generic_erp.enums;

/** State of a PMC approval task for a purchasing exception's resolution. */
public enum PurchaseExceptionApprovalStatus {
    /** Waiting for a PMC department member. */
    PENDING,
    /** PMC agreed: the exception is resolved. */
    APPROVED,
    /** PMC did not agree: the exception went back to IN_PROGRESS. */
    REJECTED
}
