package com.gsgd.generic_erp.enums;

/** Follow-up stages of a purchase order (see the purchase follow-up design). */
public enum FollowUpStage {
    /** Weeks 1-2 by default: at least twice a week. */
    INITIAL,
    /** Week 3 by default: at least every 2 days, weekends included. */
    INTENSIFIED,
    /** Week 4 by default: every day. */
    DAILY,
    /** A notification day chosen in a day-based follow-up template. */
    NOTIFY,
    /** After the last stage while the material has still not arrived. */
    OVERDUE
}
