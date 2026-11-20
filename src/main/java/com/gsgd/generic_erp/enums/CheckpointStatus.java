package com.gsgd.generic_erp.enums;

/** Life cycle of one follow-up checkpoint window. */
public enum CheckpointStatus {
    /** Waiting for a follow-up record inside its window. */
    OPEN,
    /** A follow-up record was saved inside the window. */
    DONE,
    /** The window ended with no follow-up record. */
    MISSED,
    /** Follow-up stopped (order received, closed or cancelled). */
    CANCELLED
}
