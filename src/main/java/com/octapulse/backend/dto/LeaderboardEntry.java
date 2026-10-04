package com.octapulse.backend.dto;

import java.util.UUID;

public record LeaderboardEntry(
        UUID userId,
        String username,
        long correctPicks,
        long settledPicks,
        double accuracy
) {
}
