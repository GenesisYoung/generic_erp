package com.gsgd.generic_erp.dto.purchase;

/**
 * Body of POST /api/purchase-exceptions/{id}/actions. {@code action}:
 * ACKNOWLEDGE, START, COMMENT, RESOLVE, CLOSE, REOPEN. {@code comment} is
 * required for COMMENT, RESOLVE (the resolution) and REOPEN (the reason).
 * {@code lockVersion} is the version the user saw; a newer one answers 409.
 */
public record PurchaseExceptionActionRequest(String action, String comment, Long lockVersion) {
}
