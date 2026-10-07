package com.octapulse.backend.service.notify;

import com.octapulse.backend.service.PickLock;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.octapulse.backend.service.notify.NotificationService.EVENT_REMINDER;
import static com.octapulse.backend.service.notify.NotificationService.EVENT_SETTLED;
import static com.octapulse.backend.service.notify.NotificationService.FIGHT_BOOKED;
import static com.octapulse.backend.service.notify.NotificationService.FIGHT_RESULT;

/**
 * Works out who should hear about what. Every trigger is idempotent: NotificationService
 * drops repeats by (user, type, key), so jobs can re-run freely.
 */
@Component
public class NotificationTriggers {

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notificationService;

    public NotificationTriggers(NamedParameterJdbcTemplate jdbc, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    /** Followers of either fighter hear the result. */
    public int fightResults(Collection<UUID> fightIds) {
        if (fightIds.isEmpty()) {
            return 0;
        }
        var rows = jdbc.queryForList("""
                SELECT ff.user_id, f.id AS fight_id, e.id AS event_id, e.name AS event_name,
                       f.method, f.result_round, w.name AS winner_name,
                       CASE WHEN w.id = f.red_fighter_id THEN b.name ELSE r.name END AS loser_name,
                       r.name AS red_name, b.name AS blue_name
                FROM fights f
                JOIN events e ON e.id = f.event_id
                LEFT JOIN fighters r ON r.id = f.red_fighter_id
                LEFT JOIN fighters b ON b.id = f.blue_fighter_id
                LEFT JOIN fighters w ON w.id = f.winner_fighter_id
                JOIN fighter_follows ff ON ff.fighter_id IN (f.red_fighter_id, f.blue_fighter_id)
                WHERE f.id IN (:fightIds)
                """, Map.of("fightIds", fightIds));
        int sent = 0;
        for (var row : rows) {
            String title;
            String body;
            if (row.get("winner_name") != null) {
                title = row.get("winner_name") + " def. " + row.get("loser_name");
                body = describeResult(row.get("method"), row.get("result_round")) + " at " + row.get("event_name");
            } else {
                title = row.get("red_name") + " vs " + row.get("blue_name") + ": no winner";
                body = "The fight at " + row.get("event_name") + " ended without a winner.";
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fightId", row.get("fight_id").toString());
            data.put("eventId", row.get("event_id").toString());
            if (notificationService.notify((UUID) row.get("user_id"), FIGHT_RESULT, row.get("fight_id").toString(),
                    title, body, data)) {
                sent++;
            }
        }
        return sent;
    }

    /** Users whose picks on the event are now all settled get their score. */
    public int eventSettled(UUID eventId) {
        var rows = jdbc.queryForList("""
                SELECT p.user_id, e.name AS event_name,
                       COUNT(*) FILTER (WHERE p.is_correct) AS correct,
                       COUNT(*) FILTER (WHERE p.is_correct IS NOT NULL) AS settled,
                       COALESCE(SUM(p.points), 0) AS points
                FROM picks p
                JOIN fights f ON f.id = p.fight_id
                JOIN events e ON e.id = f.event_id
                JOIN users u ON u.id = p.user_id
                WHERE f.event_id = :eventId AND u.role <> 'agent'
                GROUP BY p.user_id, e.name
                HAVING COUNT(*) FILTER (WHERE p.settled_at IS NULL) = 0
                """, Map.of("eventId", eventId));
        int sent = 0;
        for (var row : rows) {
            long correct = ((Number) row.get("correct")).longValue();
            long settled = ((Number) row.get("settled")).longValue();
            long points = ((Number) row.get("points")).longValue();
            String title = "Your " + row.get("event_name") + " picks are in";
            String body = correct + "/" + settled + " correct, +" + points + " pts";
            Map<String, Object> data = Map.of("eventId", eventId.toString(), "correct", correct,
                    "settled", settled, "points", points);
            if (notificationService.notify((UUID) row.get("user_id"), EVENT_SETTLED, eventId.toString(),
                    title, body, data)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * Reminds active users (picked in the last 90 days, or following a fighter on the card)
     * about events starting within 24h where they still have open fights to pick.
     */
    public int upcomingEventReminders() {
        Instant now = Instant.now();
        var rows = jdbc.queryForList("""
                WITH upcoming AS (
                    SELECT e.id AS event_id, e.name AS event_name, f.id AS fight_id,
                           f.red_fighter_id, f.blue_fighter_id
                    FROM events e
                    JOIN fights f ON f.event_id = e.id
                    WHERE e.starts_at BETWEEN :now AND :until AND NOT %s
                ),
                audience AS (
                    SELECT DISTINCT u.event_id, x.user_id
                    FROM upcoming u
                    JOIN (
                        SELECT user_id, NULL::uuid AS fighter_id FROM picks
                        WHERE created_at > :activeSince
                        UNION
                        SELECT user_id, fighter_id FROM fighter_follows
                    ) x ON x.fighter_id IS NULL OR x.fighter_id IN (u.red_fighter_id, u.blue_fighter_id)
                )
                SELECT a.user_id, a.event_id, MIN(u.event_name) AS event_name,
                       COUNT(*) FILTER (WHERE p.id IS NULL) AS unpicked
                FROM audience a
                JOIN upcoming u ON u.event_id = a.event_id
                JOIN users usr ON usr.id = a.user_id AND usr.role <> 'agent'
                LEFT JOIN picks p ON p.fight_id = u.fight_id AND p.user_id = a.user_id
                GROUP BY a.user_id, a.event_id
                HAVING COUNT(*) FILTER (WHERE p.id IS NULL) > 0
                """.formatted(PickLock.SQL_LOCKED),
                Map.of("now", Timestamp.from(now),
                        "until", Timestamp.from(now.plus(24, ChronoUnit.HOURS)),
                        "activeSince", Timestamp.from(now.minus(90, ChronoUnit.DAYS))));
        int sent = 0;
        for (var row : rows) {
            long unpicked = ((Number) row.get("unpicked")).longValue();
            String title = row.get("event_name") + " starts within 24 hours";
            String body = "You have " + unpicked + " fight" + (unpicked == 1 ? "" : "s") + " left to pick.";
            String eventId = row.get("event_id").toString();
            if (notificationService.notify((UUID) row.get("user_id"), EVENT_REMINDER, eventId, title, body,
                    Map.of("eventId", eventId, "unpicked", unpicked))) {
                sent++;
            }
        }
        return sent;
    }

    /** Followers hear when a fighter they follow appears on an upcoming card. */
    public int bookedFights() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ff.user_id, ff.fighter_id, f.id AS fight_id, e.id AS event_id, e.name AS event_name,
                       e.starts_at, r.name AS red_name, b.name AS blue_name
                FROM fights f
                JOIN events e ON e.id = f.event_id
                JOIN fighter_follows ff ON ff.fighter_id IN (f.red_fighter_id, f.blue_fighter_id)
                LEFT JOIN fighters r ON r.id = f.red_fighter_id
                LEFT JOIN fighters b ON b.id = f.blue_fighter_id
                WHERE COALESCE(f.starts_at, e.starts_at) > now()
                  AND NOT EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.user_id = ff.user_id AND n.dedupe_key = 'fight_booked:' || f.id::text
                  )
                """, Map.of());
        int sent = 0;
        for (var row : rows) {
            String fightId = row.get("fight_id").toString();
            String title = row.get("red_name") + " vs " + row.get("blue_name") + " is booked";
            String body = "On the card at " + row.get("event_name") + ".";
            Map<String, Object> data = Map.of("fightId", fightId, "eventId", row.get("event_id").toString(),
                    "fighterId", row.get("fighter_id").toString());
            if (notificationService.notify((UUID) row.get("user_id"), FIGHT_BOOKED, fightId, title, body, data)) {
                sent++;
            }
        }
        return sent;
    }

    private static String describeResult(Object method, Object round) {
        String m = method == null ? "Result" : method.toString();
        return round == null ? m : m + ", round " + round;
    }
}
