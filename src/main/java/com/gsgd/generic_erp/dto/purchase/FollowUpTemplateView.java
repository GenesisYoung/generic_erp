package com.gsgd.generic_erp.dto.purchase;

import java.util.List;

import com.gsgd.generic_erp.enums.FollowUpStage;

/**
 * A follow-up template as the template page sees it.
 *
 * @param dayBased     true when it uses notification days; false for the
 *                     stage-based built-in rule (then {@code stages} is filled)
 * @param activeOrders purchase orders still being followed up with this template
 */
public record FollowUpTemplateView(
        Long id,
        String name,
        String materialNote,
        Integer productionDays,
        List<Integer> notifyDays,
        int overduePeriodDays,
        boolean isDefault,
        boolean dayBased,
        List<Stage> stages,
        long activeOrders) {

    public record Stage(FollowUpStage stage, int startDay, int endDay, int periodDays, int checksPerPeriod) {
    }
}
