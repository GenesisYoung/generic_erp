package com.gsgd.generic_erp.enums;

/**
 * Section of the placement task page. Sample orders and production orders come
 * from different old-ERP documents and are shown separately; the purchase
 * orders placed for them are numbered YP... and P... in the old ERP.
 */
public enum PlacementOrderType {
    /** Purchase plan (采购计划, PLANORDER1/2); its POs are numbered P... */
    PRODUCTION,
    /** Purchase application (采购申请, ORDREQ1/2); its POs are numbered YP... */
    SAMPLE,
    /** Not used for new tasks; listed with PRODUCTION. */
    OTHER
}
