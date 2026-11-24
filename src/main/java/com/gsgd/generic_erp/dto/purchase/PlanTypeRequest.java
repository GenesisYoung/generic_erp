package com.gsgd.generic_erp.dto.purchase;

/**
 * Body of {@code POST /api/followup-plan-groups/types} (add a type by hand) and
 * {@code PUT /api/followup-plan-groups/types/{id}} (rename it or move it to a group).
 *
 * @param code    required when adding, at most 50 characters; ignored when changing
 * @param name    optional, at most 100 characters
 * @param groupId the group it belongs to; absent = no group (main task)
 */
public record PlanTypeRequest(String code, String name, Long groupId) {
}
