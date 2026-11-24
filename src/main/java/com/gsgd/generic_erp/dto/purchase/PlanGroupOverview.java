package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDateTime;
import java.util.List;

/** What the plan groups page shows: the groups and every known plan type. */
public record PlanGroupOverview(List<Group> groups, List<Type> types, boolean linesSynced) {

    /**
     * @param openOrders active orders that have this group's own task now
     */
    public record Group(Long id, String name, boolean separate, Long templateId, String templateName, String note,
            List<String> typeCodes, long openOrders) {
    }

    /**
     * @param lineCount  stored purchase order lines with this type
     * @param lastSeenAt last time the sync saw it; null for a type added by hand
     */
    public record Type(Long id, String code, String name, Long groupId, long lineCount, LocalDateTime lastSeenAt) {
    }
}
