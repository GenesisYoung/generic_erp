package com.gsgd.generic_erp.service.purchase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Runs {@link PlacementTaskService#runOnce()} on a schedule (default every 15
 * minutes). With the default trigger (CONFIRMATION) new tasks come from the
 * old ERP's purchase applications and purchase plans, so they appear only when
 * {@code erp.legacy.enabled=true}; reminders and PO-link closing run either
 * way. Turn it off with {@code erp.purchase.placement.enabled=false}.
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
            if (r.created() + r.skipped() + r.closed() + r.confirmed() + r.reminded() + r.overdue() > 0) {
                log.info("Placement tasks: created {}, skipped {}, closed by PO {}, closed by confirmation {},"
                        + " reminded {}, overdue {}", r.created(), r.skipped(), r.closed(), r.confirmed(),
                        r.reminded(), r.overdue());
            }
        } catch (Exception e) {
            log.error("Placement task run failed", e);
        }
    }
}
