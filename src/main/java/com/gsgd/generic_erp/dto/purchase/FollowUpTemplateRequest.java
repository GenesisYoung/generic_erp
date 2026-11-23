package com.gsgd.generic_erp.dto.purchase;

import java.util.List;

/**
 * Body to create or change a follow-up template.
 *
 * @param name              1 to 100 characters
 * @param materialNote      optional: which materials it is for, up to 200 characters
 * @param productionDays    expected production period, 1 to 365 calendar days after the order date
 * @param notifyDays        notification days, each 1 to productionDays; 1 to 60 of them
 * @param overduePeriodDays days per follow-up task after the production period while
 *                          the material has not arrived; 0 = none
 */
public record FollowUpTemplateRequest(String name, String materialNote, Integer productionDays,
        List<Integer> notifyDays, Integer overduePeriodDays) {
}
