package com.gsgd.generic_erp.dto.purchase;

import java.util.List;

/**
 * Body of {@code POST/PUT /api/followup-plan-groups}.
 *
 * @param name       required, at most 100 characters
 * @param separate   true: lines of these plan types get their own follow-up task; false/absent: they stay in the
 *                   order's main task
 * @param templateId template for the group's own task; absent = the default template
 * @param note       optional, at most 200 characters
 * @param typeCodes  plan types that count as this plan; a type in another group moves here. Absent = unchanged
 */
public record PlanGroupRequest(
        String name,
        Boolean separate,
        Long templateId,
        String note,
        List<String> typeCodes) {
}
