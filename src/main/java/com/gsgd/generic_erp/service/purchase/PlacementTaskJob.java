package com.gsgd.generic_erp.service.purchase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Runs {@link PlacementTaskService#runOnce()} on a schedule (default every 15
 * minutes). It does not need the old ERP connection: tasks come from order
 * audits in this system, and links come from the customer order sync. Turn it
 * off with {@code erp.purchase.placement.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.purchase.placement", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlacementTaskJob {

    private static final Logger log = LoggerFactory.getLogger(PlacementTaskJob.class);

    private final PlacementTaskService service;

    @Scheduled(cron = "${erp.purchase.placement.cron:0 */15 * * * *}", zone = "${erp.purchase.zone:Asia/Shanghai}")
    public void run() {
        try {
            PlacementTaskService.RunResult r = service.runOnce();
            if (r.created() + r.skipped() + r.closed() + r.reminded() + r.overdue() > 0) {
                log.info("Placement tasks: created {}, skipped {}, closed {}, reminded {}, overdue {}", r.created(),
                        r.skipped(), r.closed(), r.reminded(), r.overdue());
            }
        } catch (Exception e) {
            log.error("Placement task run failed", e);
        }
    }
}
