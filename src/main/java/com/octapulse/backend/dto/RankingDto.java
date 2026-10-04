package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Ranking;

import java.time.Instant;
import java.util.UUID;

public record RankingDto(
        String division,
        Integer rank,
        UUID fighterId,
        boolean champion,
        Instant fetchedAt
) {
    public static RankingDto from(Ranking r) {
        return new RankingDto(r.getDivision(), r.getRank(), r.getFighterId(), r.isChampion(), r.getFetchedAt());
    }
}
