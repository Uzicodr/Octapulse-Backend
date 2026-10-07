package com.octapulse.backend.dto;

import java.util.List;

public record StatsDto(
        long totalPicks,
        long pendingPicks,
        long settledPicks,
        long correctPicks,
        long voidPicks,
        double accuracy,
        long points,
        int currentStreak,
        int bestStreak,
        Long globalRank,
        List<DivisionStats> byDivision
) {
    public record DivisionStats(String division, long settledPicks, long correctPicks, double accuracy) {
    }
}
