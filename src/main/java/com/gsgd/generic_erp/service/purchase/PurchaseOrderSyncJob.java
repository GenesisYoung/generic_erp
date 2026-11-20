package com.gsgd.generic_erp.service.purchase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Runs the purchase order sync on a schedule, only when
 * {@code erp.legacy.enabled=true}. A failure is logged and stored in
 * legacy_sync_state_tb; the next run retries from the same point.
 * <p>
 * If the backend ever runs on more than one instance, add a lock (Redis is
 * already configured) so only one instance syncs at a time.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.legacy", name = "enabled", havingValue = "true")
public class PurchaseOrderSyncJob {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSyncJob.class);

    private final PurchaseOrderSyncService syncService;

    @Scheduled(cron = "${erp.legacy.purchase-order.sync-cron:0 */15 * * * *}", zone = "${erp.purchase.zone:Asia/Shanghai}")
    public void run() {
        try {
            PurchaseOrderSyncService.SyncResult r = syncService.syncNow();
            log.info("Purchase order sync: read {}, created {}, updated {}, closed {}", r.rowsRead(), r.created(),
                    r.updated(), r.closed());
        } catch (Exception e) {
            log.error("Purchase order sync failed", e);
            syncService.recordFailure(e);
        }
    }
}
