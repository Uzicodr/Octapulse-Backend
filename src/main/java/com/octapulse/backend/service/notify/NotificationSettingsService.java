package com.octapulse.backend.service.notify;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-user switches for each kind of push. A user without a row gets everything. */
@Service
public class NotificationSettingsService {

    public record Settings(boolean live, boolean results, boolean news, boolean announcements, boolean reminders) {
        static final Settings ALL_ON = new Settings(true, true, true, true, true);

        /** Whether a notification type may be pushed. Unknown types are allowed. */
        public boolean allows(String type) {
            return switch (type) {
                case NotificationService.EVENT_LIVE -> live;
                case NotificationService.FIGHT_RESULT, NotificationService.EVENT_SETTLED -> results;
                case NotificationService.FIGHTER_NEWS -> news;
                case NotificationService.FIGHT_BOOKED -> announcements;
                case NotificationService.EVENT_REMINDER -> reminders;
                default -> true;
            };
        }
    }

    /** PATCH body: null fields keep their current value. */
    public record Update(Boolean live, Boolean results, Boolean news, Boolean announcements, Boolean reminders) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationSettingsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Settings get(UUID userId) {
        List<Settings> rows = jdbc.query(
                "SELECT live, results, news, announcements, reminders FROM notification_settings WHERE user_id = :userId",
                Map.of("userId", userId),
                (rs, i) -> new Settings(rs.getBoolean("live"), rs.getBoolean("results"), rs.getBoolean("news"),
                        rs.getBoolean("announcements"), rs.getBoolean("reminders")));
        return rows.isEmpty() ? Settings.ALL_ON : rows.get(0);
    }

    public Settings update(UUID userId, Update update) {
        Settings current = get(userId);
        Settings next = new Settings(
                update.live() != null ? update.live() : current.live(),
                update.results() != null ? update.results() : current.results(),
                update.news() != null ? update.news() : current.news(),
                update.announcements() != null ? update.announcements() : current.announcements(),
                update.reminders() != null ? update.reminders() : current.reminders());
        jdbc.update("""
                INSERT INTO notification_settings (user_id, live, results, news, announcements, reminders, updated_at)
                VALUES (:userId, :live, :results, :news, :announcements, :reminders, now())
                ON CONFLICT (user_id) DO UPDATE SET live = EXCLUDED.live, results = EXCLUDED.results,
                    news = EXCLUDED.news, announcements = EXCLUDED.announcements,
                    reminders = EXCLUDED.reminders, updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("live", next.live())
                .addValue("results", next.results())
                .addValue("news", next.news())
                .addValue("announcements", next.announcements())
                .addValue("reminders", next.reminders()));
        return next;
    }
}
