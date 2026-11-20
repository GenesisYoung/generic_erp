package com.gsgd.generic_erp.dto.purchase;

/** Body of POST /api/placement-tasks/{id}/complete: the old-ERP PO number that was placed. */
public record PlacementCompleteRequest(String poNo) {
}
