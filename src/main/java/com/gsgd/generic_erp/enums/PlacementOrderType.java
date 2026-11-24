package com.gsgd.generic_erp.enums;

/**
 * Kind of order a placement task is for, told apart by the order number
 * prefix ({@code erp.purchase.placement.sample-prefix} /
 * {@code production-prefix}). Sample orders and production orders are shown in
 * separate sections and come from different old-ERP documents.
 */
public enum PlacementOrderType {
    /** Order number starts with "P"; source: purchase plan (采购计划, PLANORDER1/2). */
    PRODUCTION,
    /** Order number starts with "YP"; source: purchase application (采购申请, ORDREQ1/2). */
    SAMPLE,
    /** Neither prefix (only possible for tasks made from order audits). */
    OTHER
}
