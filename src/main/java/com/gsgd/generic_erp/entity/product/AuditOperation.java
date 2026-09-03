package com.gsgd.generic_erp.entity.product;

/** Operation recorded by the product audit log. */
public enum AuditOperation {
    /** Record creation. */
    CREATE,
    /** Change to an ordinary field. */
    UPDATE,
    /** Change to a lifecycle status. */
    STATUS_CHANGE,
    /** Record deletion. */
    DELETE
}
