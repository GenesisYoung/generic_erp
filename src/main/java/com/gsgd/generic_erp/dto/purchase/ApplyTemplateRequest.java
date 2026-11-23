package com.gsgd.generic_erp.dto.purchase;

/** Body of PUT /api/purchase-orders/{id}/followup-template. */
public record ApplyTemplateRequest(Long templateId) {
}
