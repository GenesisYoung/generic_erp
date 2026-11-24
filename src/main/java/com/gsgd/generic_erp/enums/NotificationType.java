package com.gsgd.generic_erp.enums;

/**
 * What a stored notification is about. The purchase order follow-up module
 * raises all of these; the frontend uses the value to pick an icon and the
 * page to open when the notification is clicked.
 */
public enum NotificationType {
    /** An order review task was assigned to a PMC clerk. */
    REVIEW_ASSIGNED,
    /** A purchase order is close to, or past, its placement deadline. */
    PO_PLACEMENT_DUE,
    /** A follow-up checkpoint window has opened. */
    FOLLOWUP_DUE,
    /** A follow-up checkpoint passed its deadline with no record. */
    FOLLOWUP_MISSED,
    /** A purchaser reported a purchasing exception. */
    EXCEPTION_REPORTED,
    /** A purchasing exception changed status or got a comment. */
    EXCEPTION_UPDATED,
    /** A purchasing exception's resolution waits for the PMC department's approval. */
    EXCEPTION_APPROVAL,
    /** New customer orders arrived from the old ERP and need an order audit. */
    CUSTOMER_ORDER_NEW,
    /** Anything else. */
    GENERAL
}
