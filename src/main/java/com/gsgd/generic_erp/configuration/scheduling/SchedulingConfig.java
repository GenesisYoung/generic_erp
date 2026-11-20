package com.gsgd.generic_erp.configuration.scheduling;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs and provides the business clock.
 * <p>
 * Follow-up windows are calendar dates in the business time zone
 * ({@code erp.purchase.zone}, default Asia/Shanghai), so "today" must be
 * computed there and not in the server's own zone. Tests replace the clock
 * with a fixed one.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock erpBusinessClock(@Value("${erp.purchase.zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
