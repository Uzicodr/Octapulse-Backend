package com.octapulse.backend.service;

import com.octapulse.backend.dto.LeaderboardEntry;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LeaderboardService {

    public enum Scope { ALL, MONTH, EVENT, FOLLOWING, LEAGUE }

    // Ranked by points, then accuracy, then volume. Only settled, non-void picks count.
    private static final String RANKED = """
            WITH scored AS (
                SELECT p.user_id,
                       COUNT(*) FILTER (WHERE p.is_correct) AS correct,
                       COUNT(*) AS settled,
                       COALESCE(SUM(p.points), 0) AS points
                FROM picks p
                JOIN fights f ON f.id = p.fight_id
                WHERE p.is_correct IS NOT NULL %s
                GROUP BY p.user_id
            ),
            ranked AS (
                SELECT s.*, RANK() OVER (ORDER BY s.points DESC, s.correct::float / s.settled DESC) AS rnk
                FROM scored s
            )
            SELECT r.user_id, u.username, u.display_name, u.avatar_url, u.role,
                   r.correct, r.settled, r.points, r.rnk
            FROM ranked r
            JOIN users u ON u.id = r.user_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LeaderboardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LeaderboardEntry> leaderboard(Scope scope, UUID eventId, UUID leagueId, UUID viewerId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        String filter = filter(scope, eventId, leagueId, viewerId, params);
        return jdbc.query(RANKED.formatted(filter) + " ORDER BY r.rnk, r.settled DESC, u.username LIMIT :limit",
                params, (rs, i) -> {
                    long correct = rs.getLong("correct");
                    long settled = rs.getLong("settled");
                    return new LeaderboardEntry(
                            rs.getObject("user_id", UUID.class),
                            rs.getString("username"),
                            correct,
                            settled,
                            settled == 0 ? 0.0 : (double) correct / settled,
                            rs.getLong("rnk"),
                            rs.getLong("points"),
                            rs.getString("display_name"),
                            rs.getString("avatar_url"),
                            "agent".equals(rs.getString("role"))
                    );
                });
    }

    /** All-time rank for one user, or null if they have no settled picks. */
    public Long globalRank(UUID userId) {
        var rows = jdbc.queryForList(RANKED.formatted("") + " WHERE r.user_id = :userId",
                new MapSqlParameterSource("userId", userId));
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("rnk")).longValue();
    }

    private String filter(Scope scope, UUID eventId, UUID leagueId, UUID viewerId, MapSqlParameterSource params) {
        switch (scope) {
            case ALL:
                return "";
            case MONTH:
                return "AND p.settled_at >= date_trunc('month', now())";
            case EVENT:
                if (eventId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required for scope=event");
                }
                params.addValue("eventId", eventId);
                return "AND f.event_id = :eventId";
            case FOLLOWING:
                requireViewer(viewerId);
                params.addValue("viewerId", viewerId);
                return """
                        AND (p.user_id = :viewerId
                             OR p.user_id IN (SELECT followee_id FROM follows WHERE follower_id = :viewerId))
                        """;
            case LEAGUE:
                requireViewer(viewerId);
                if (leagueId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leagueId is required for scope=league");
                }
                Boolean member = jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM league_members WHERE league_id = :leagueId AND user_id = :viewerId)",
                        new MapSqlParameterSource("leagueId", leagueId).addValue("viewerId", viewerId), Boolean.class);
                if (!Boolean.TRUE.equals(member)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this league");
                }
                params.addValue("leagueId", leagueId);
                return "AND p.user_id IN (SELECT user_id FROM league_members WHERE league_id = :leagueId)";
            default:
                throw new IllegalStateException(scope.name());
        }
    }

    private static void requireViewer(UUID viewerId) {
        if (viewerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to see this leaderboard");
        }
    }
}
