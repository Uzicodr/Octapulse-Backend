package com.octapulse.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work. On Render's free plan the instance sleeps when idle, so these only
 * run while the service is awake; every job is idempotent and catches up on the next run.
 * The agent also wakes the service through POST /internal/notify when it has fresh results or news.
 */
@Component
@ConditionalOnProperty(name = "jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final NotificationJobs jobs;

    public ScheduledJobs(NotificationJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${jobs.settlement-interval:PT5M}", initialDelayString = "PT1M")
    public void settle() {
        try {
            jobs.settleAndNotify();
        } catch (RuntimeException e) {
            log.error("settlement job failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${jobs.reminder-interval:PT1H}", initialDelayString = "PT2M")
    public void remind() {
        try {
            var sent = jobs.remind();
            int total = sent.values().stream().mapToInt(Integer::intValue).sum();
            if (total > 0) {
                log.info("sent {} reminder notifications", total);
            }
        } catch (RuntimeException e) {
            log.error("reminder job failed", e);
        }
    }
}
