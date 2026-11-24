package com.gsgd.generic_erp.dto.purchase;

import java.util.List;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrderLine;

/**
 * Everything the order detail page needs about follow-up: the order, its task
 * series (main first), its windows (each with its {@code planGroupId}), its
 * lines and its records (newest first).
 */
public record PurchaseOrderFollowUps(
        PurchaseOrder order,
        List<FollowUpCheckpoint> checkpoints,
        List<FollowUpRecordView> records,
        List<FollowUpSeriesView> series,
        List<PurchaseOrderLine> lines) {

    public PurchaseOrderFollowUps(PurchaseOrder order, List<FollowUpCheckpoint> checkpoints,
            List<FollowUpRecordView> records) {
        this(order, checkpoints, records, List.of(), List.of());
    }
}
