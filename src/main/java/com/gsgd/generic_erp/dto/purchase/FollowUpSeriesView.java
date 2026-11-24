package com.gsgd.generic_erp.dto.purchase;

/**
 * One follow-up task series of a purchase order: the main series
 * ({@code planGroupId} null) or the separate series of one plan group.
 *
 * @param active    false once the series has nothing left to follow
 * @param lineCount lines of the order in this series
 * @param openLines of those, lines still waiting for goods
 */
public record FollowUpSeriesView(
        Long planGroupId,
        String planGroupName,
        Long templateId,
        String templateName,
        boolean active,
        int lineCount,
        int openLines) {
}
