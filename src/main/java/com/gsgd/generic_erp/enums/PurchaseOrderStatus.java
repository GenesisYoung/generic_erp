package com.gsgd.generic_erp.enums;

/**
 * Status of a purchase order as this system understands it. Raw statuses from
 * the old ERP are mapped onto these values by configuration
 * ({@code erp.legacy.purchase-order.status-map}).
 */
public enum PurchaseOrderStatus {
    PLACED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CLOSED,
    CANCELLED;

    /** True while the order still needs follow-up. */
    public boolean isActive() {
        return this == PLACED || this == PARTIALLY_RECEIVED;
    }
}
