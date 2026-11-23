package com.gsgd.generic_erp.service.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Runs the customer order sync on a schedule (default every 15 minutes), only
 * when {@code erp.legacy.enabled=true}. A failure is logged and stored in
 * legacy_sync_state_tb (key CUSTOMER_ORDER); the next run retries.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.legacy", name = "enabled", havingValue = "true")
public class CustomerOrderSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderSyncJob.class);

    private final CustomerOrderSyncService syncService;

    @Scheduled(cron = "${erp.legacy.customer-order.sync-cron:0 */15 * * * *}", zone = "${erp.purchase.zone:Asia/Shanghai}")
    public void run() {
        try {
            CustomerOrderSyncService.SyncResult r = syncService.syncNow();
            log.info("Customer order sync: read {}, created {}, updated {}, new open {}, notified {}", r.rowsRead(),
                    r.created(), r.updated(), r.newOpen(), r.notified());
        } catch (Exception e) {
            log.error("Customer order sync failed", e);
            syncService.recordFailure(e);
        }
    }
}
