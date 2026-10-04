package com.octapulse.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sync")
public class SyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final SyncService syncService;

    public SyncRunner(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Usage (set via SPRING_APPLICATION_ARGUMENTS or program args):
     *   events            -- sync page 1 of events
     *   rankings          -- sync rankings snapshot
     *   bouts <eventSlug> -- sync one event's bout card
     */
    @Override
    public void run(ApplicationArguments args) {
        var nonOption = args.getNonOptionArgs();
        if (nonOption.isEmpty()) {
            log.warn("no sync command given; expected events | rankings | bouts <eventSlug>");
            return;
        }

        String command = nonOption.get(0);
        try {
            switch (command) {
                case "events" -> syncService.syncEventsPage(1, 50);
                case "rankings" -> syncService.syncRankings();
                case "bouts" -> {
                    if (nonOption.size() < 2) {
                        log.warn("bouts command requires an event slug argument");
                        break;
                    }
                    syncService.syncBouts(nonOption.get(1));
                }
                default -> log.warn("unknown sync command: {}", command);
            }
        } catch (BudgetExceededException e) {
            log.error("sync aborted: {}", e.getMessage());
        }
    }
}
