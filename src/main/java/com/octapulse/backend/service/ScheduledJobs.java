package com.octapulse.backend.service;

import com.octapulse.backend.service.notify.NotificationTriggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work. On Render's free plan the instance sleeps when idle, so these only
 * run while the service is awake; every job is idempotent and catches up on the next run.
 */
@Component
@ConditionalOnProperty(name = "jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final SettlementService settlementService;
    private final NotificationTriggers triggers;

    public ScheduledJobs(SettlementService settlementService, NotificationTriggers triggers) {
        this.settlementService = settlementService;
        this.triggers = triggers;
    }

    @Scheduled(fixedDelayString = "${jobs.settlement-interval:PT5M}", initialDelayString = "PT1M")
    public void settle() {
        try {
            var settled = settlementService.settlePending();
            triggers.fightResults(settled.fightIds());
            settled.eventIds().forEach(triggers::eventSettled);
        } catch (RuntimeException e) {
            log.error("settlement job failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${jobs.reminder-interval:PT1H}", initialDelayString = "PT2M")
    public void remind() {
        try {
            int sent = triggers.upcomingEventReminders();
            sent += triggers.bookedFights();
            if (sent > 0) {
                log.info("sent {} reminder notifications", sent);
            }
        } catch (RuntimeException e) {
            log.error("reminder job failed", e);
        }
    }
}
