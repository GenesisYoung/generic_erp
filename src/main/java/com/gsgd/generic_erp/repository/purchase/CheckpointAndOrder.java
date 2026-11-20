package com.gsgd.generic_erp.repository.purchase;

import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;

/** A checkpoint loaded together with its purchase order, so callers avoid one query per row. */
public record CheckpointAndOrder(FollowUpCheckpoint checkpoint, PurchaseOrder order) {
}
