package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDate;

import com.gsgd.generic_erp.enums.MaterialStatus;

/**
 * Body of {@code POST /api/purchase-orders/{id}/followups}. The follow-up time
 * is always the server's time, so a record cannot be back-dated into a window
 * that has already ended.
 *
 * @param materialStatus       required
 * @param supplierPromisedDate optional delivery date the supplier promised
 * @param missingOrderFound    optional, false when absent
 * @param note                 optional, at most 500 characters
 * @param planGroupId          optional: the plan group whose separate follow-up task this is for;
 *                             absent = the order's main task
 */
public record FollowUpRecordRequest(
        MaterialStatus materialStatus,
        LocalDate supplierPromisedDate,
        Boolean missingOrderFound,
        String note,
        Long planGroupId) {

    public FollowUpRecordRequest(MaterialStatus materialStatus, LocalDate supplierPromisedDate,
            Boolean missingOrderFound, String note) {
        this(materialStatus, supplierPromisedDate, missingOrderFound, note, null);
    }
}
