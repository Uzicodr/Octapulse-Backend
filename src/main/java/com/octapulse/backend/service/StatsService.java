package com.octapulse.backend.service;

import com.octapulse.backend.dto.StatsDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StatsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final LeaderboardService leaderboardService;

    public StatsService(NamedParameterJdbcTemplate jdbc, LeaderboardService leaderboardService) {
        this.jdbc = jdbc;
        this.leaderboardService = leaderboardService;
    }

    public StatsDto forUser(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);

        var totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE settled_at IS NULL) AS pending,
                       COUNT(*) FILTER (WHERE is_correct IS NOT NULL) AS settled,
                       COUNT(*) FILTER (WHERE is_correct) AS correct,
                       COUNT(*) FILTER (WHERE settled_at IS NOT NULL AND is_correct IS NULL) AS voided,
                       COALESCE(SUM(points), 0) AS points
                FROM picks WHERE user_id = :userId
                """, params);

        // Settled picks in fight order, oldest first, for streaks.
        List<Boolean> results = jdbc.queryForList("""
                SELECT p.is_correct
                FROM picks p
                JOIN fights f ON f.id = p.fight_id
                JOIN events e ON e.id = f.event_id
                WHERE p.user_id = :userId AND p.is_correct IS NOT NULL
                ORDER BY COALESCE(f.starts_at, e.starts_at), f.bout_order DESC NULLS LAST, p.settled_at
                """, params, Boolean.class);
        int best = 0;
        int run = 0;
        for (Boolean correct : results) {
            run = correct ? run + 1 : 0;
            best = Math.max(best, run);
        }

        List<StatsDto.DivisionStats> byDivision = jdbc.query("""
                SELECT COALESCE(f.weight_class, 'Unknown') AS division,
                       COUNT(*) AS settled,
                       COUNT(*) FILTER (WHERE p.is_correct) AS correct
                FROM picks p
                JOIN fights f ON f.id = p.fight_id
                WHERE p.user_id = :userId AND p.is_correct IS NOT NULL
                GROUP BY 1
                ORDER BY settled DESC, division
                """, params, (rs, i) -> {
            long settled = rs.getLong("settled");
            long correct = rs.getLong("correct");
            return new StatsDto.DivisionStats(rs.getString("division"), settled, correct,
                    settled == 0 ? 0.0 : (double) correct / settled);
        });

        long settled = ((Number) totals.get("settled")).longValue();
        long correct = ((Number) totals.get("correct")).longValue();
        return new StatsDto(
                ((Number) totals.get("total")).longValue(),
                ((Number) totals.get("pending")).longValue(),
                settled,
                correct,
                ((Number) totals.get("voided")).longValue(),
                settled == 0 ? 0.0 : (double) correct / settled,
                ((Number) totals.get("points")).longValue(),
                run,
                best,
                leaderboardService.globalRank(userId),
                byDivision
        );
    }
}
