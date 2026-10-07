package com.octapulse.backend.service;

import com.octapulse.backend.service.notify.NotificationTriggers;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The work behind every notification: settle finished fights, then tell people. Run on a timer by
 * ScheduledJobs and on demand through POST /internal/notify, which the agent calls right after it
 * saves live results or news so pushes don't wait for the sleeping free instance to wake up.
 * Every step is idempotent; notifications are de-duplicated per user.
 */
@Service
public class NotificationJobs {

    private final SettlementService settlementService;
    private final NotificationTriggers triggers;
    private final NamedParameterJdbcTemplate jdbc;

    public NotificationJobs(SettlementService settlementService, NotificationTriggers triggers,
                            NamedParameterJdbcTemplate jdbc) {
        this.settlementService = settlementService;
        this.triggers = triggers;
        this.jdbc = jdbc;
    }

    /** Settles results and sends live, result, settled-event and news notifications. */
    public synchronized Map<String, Integer> settleAndNotify() {
        var settled = settlementService.settlePending();
        // Also fights finished recently that nobody picked, so their followers still hear the result.
        Set<UUID> fightIds = new LinkedHashSet<>(settled.fightIds());
        fightIds.addAll(recentlyFinishedFights());
        Map<String, Integer> sent = new LinkedHashMap<>();
        sent.put("live", triggers.eventsLive());
        sent.put("results", triggers.fightResults(fightIds));
        sent.put("settled", settled.eventIds().stream().mapToInt(triggers::eventSettled).sum());
        sent.put("news", triggers.fighterNews());
        return sent;
    }

    /** Reminders for events starting soon and newly booked fights. */
    public synchronized Map<String, Integer> remind() {
        Map<String, Integer> sent = new LinkedHashMap<>();
        sent.put("reminders", triggers.upcomingEventReminders());
        sent.put("booked", triggers.bookedFights());
        return sent;
    }

    private List<UUID> recentlyFinishedFights() {
        return jdbc.queryForList(
                "SELECT id FROM fights WHERE status = 'completed' AND updated_at > :since",
                Map.of("since", Timestamp.from(Instant.now().minus(3, ChronoUnit.HOURS))), UUID.class);
    }
}
