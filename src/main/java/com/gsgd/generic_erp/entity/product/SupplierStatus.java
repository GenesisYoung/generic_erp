package com.gsgd.generic_erp.entity.product;

/** Lifecycle status of a supplier. */
public enum SupplierStatus {
    /** All supplier operations are available. */
    ACTIVE,
    /** New purchases are blocked, but existing business may still be processed. */
    HOLD,
    /** New purchasing, receiving, and payment operations are blocked. */
    INACTIVE
}
