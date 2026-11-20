package com.gsgd.generic_erp.dto.purchase;

import java.util.List;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;

/** Everything the order detail page needs about follow-up: the order, its windows and its records (newest first). */
public record PurchaseOrderFollowUps(
        PurchaseOrder order,
        List<FollowUpCheckpoint> checkpoints,
        List<FollowUpRecordView> records) {
}
