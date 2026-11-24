package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gsgd.generic_erp.entity.purchase.FollowUpRecord;
import com.gsgd.generic_erp.enums.MaterialStatus;

/** A follow-up record as the API returns it, with the name of the user who saved it. */
public record FollowUpRecordView(
        Long id,
        Long purchaseOrderId,
        Long checkpointId,
        Long recordedBy,
        String recordedByName,
        MaterialStatus materialStatus,
        LocalDate supplierPromisedDate,
        boolean missingOrderFound,
        String note,
        LocalDateTime followedUpAt,
        Long planGroupId) {

    public static FollowUpRecordView of(FollowUpRecord r, String recordedByName) {
        return new FollowUpRecordView(r.getId(), r.getPurchaseOrderId(), r.getCheckpointId(), r.getRecordedBy(),
                recordedByName, r.getMaterialStatus(), r.getSupplierPromisedDate(), r.isMissingOrderFound(),
                r.getNote(), r.getFollowedUpAt(), r.getPlanGroupId());
    }
}
