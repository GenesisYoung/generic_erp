package com.gsgd.generic_erp.enums;

/** What the purchaser found out about the material during a follow-up. */
public enum MaterialStatus {
    /** The supplier will deliver on time. */
    ON_TRACK,
    /** Delivery may be late; keep a close eye on it. */
    AT_RISK,
    /** The supplier confirmed it will be late. */
    DELAYED
}
