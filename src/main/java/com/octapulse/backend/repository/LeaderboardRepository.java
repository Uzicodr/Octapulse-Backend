package com.octapulse.backend.repository;

import com.octapulse.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LeaderboardRepository extends JpaRepository<User, UUID> {

    @Query(value = """
            SELECT u.id AS userId,
                   u.username AS username,
                   COUNT(*) FILTER (WHERE p.is_correct = TRUE) AS correctPicks,
                   COUNT(*) FILTER (WHERE p.is_correct IS NOT NULL) AS settledPicks
            FROM users u
            JOIN picks p ON p.user_id = u.id
            GROUP BY u.id, u.username
            HAVING COUNT(*) FILTER (WHERE p.is_correct IS NOT NULL) > 0
            ORDER BY (COUNT(*) FILTER (WHERE p.is_correct = TRUE))::float
                     / COUNT(*) FILTER (WHERE p.is_correct IS NOT NULL) DESC,
                     settledPicks DESC
            """, nativeQuery = true)
    List<LeaderboardRow> fetchLeaderboard();

    interface LeaderboardRow {
        UUID getUserId();
        String getUsername();
        long getCorrectPicks();
        long getSettledPicks();
    }
}
