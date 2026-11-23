package com.gsgd.generic_erp.enums;

/** How serious a purchasing exception is; decides the notification priority. */
public enum PurchaseExceptionSeverity {
    NORMAL(NotifactionPriority.NORMAL), HIGH(NotifactionPriority.HIGH), URGENT(NotifactionPriority.ERGENT);

    private final NotifactionPriority priority;

    PurchaseExceptionSeverity(NotifactionPriority priority) {
        this.priority = priority;
    }

    public NotifactionPriority priority() {
        return priority;
    }
}
