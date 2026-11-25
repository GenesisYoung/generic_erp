package com.gsgd.generic_erp.enums;

/** What created a placement task. */
public enum PlacementSource {
    /** Order audit (PMC review) PASS. Used before 2026-11-24, or with trigger ORDER_AUDIT. */
    ORDER_AUDIT,
    /** An unconfirmed purchase plan (采购计划) in the old ERP: production orders. */
    PURCHASE_PLAN,
    /** An unconfirmed purchase application (采购申请) in the old ERP: sample orders. */
    PURCHASE_APPLICATION
}
