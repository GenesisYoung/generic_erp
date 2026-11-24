package com.gsgd.generic_erp.dto.purchase;

/**
 * Body of PUT /api/purchase-orders/{id}/followup-template.
 *
 * @param planGroupId the plan group whose own follow-up task to change; absent = the order's main task
 */
public record ApplyTemplateRequest(Long templateId, Long planGroupId) {

    public ApplyTemplateRequest(Long templateId) {
        this(templateId, null);
    }
}
