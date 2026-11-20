package com.gsgd.generic_erp.service.purchase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Runs {@link FollowUpReminderService#runOnce()} every hour (on by default;
 * turn off with {@code erp.purchase.followup.enabled=false}).
 * <p>
 * If the backend ever runs on more than one instance, add a lock (Redis is
 * already configured) so only one instance sends reminders.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.purchase.followup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FollowUpReminderJob.class);

    private final FollowUpReminderService reminderService;

    @Scheduled(cron = "${erp.purchase.followup.cron:0 0 * * * *}", zone = "${erp.purchase.zone:Asia/Shanghai}")
    public void run() {
        try {
            FollowUpReminderService.RunResult r = reminderService.runOnce();
            log.info("Follow-up job: {} missed, {} overdue windows created, {} reminded", r.missed(),
                    r.overdueCreated(), r.reminded());
        } catch (Exception e) {
            log.error("Follow-up job failed; the next run will retry", e);
        }
    }
}
