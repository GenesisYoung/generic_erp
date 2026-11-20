package com.gsgd.generic_erp.enums;

/** State of a purchase placement task. */
public enum PlacementTaskStatus {
    /** Waiting for the first purchase order. */
    OPEN,
    /** A purchase order was found (or entered); {@code doneSource} says how. */
    DONE,
    /**
     * Not needed: the audit result was not PASS, or another round of the same
     * order already has a task. Kept so the audit is not checked again.
     */
    SKIPPED
}
