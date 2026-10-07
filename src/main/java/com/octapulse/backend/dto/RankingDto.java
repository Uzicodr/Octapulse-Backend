package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.domain.Ranking;

import java.time.Instant;
import java.util.UUID;

public record RankingDto(
        String division,
        Integer rank,
        UUID fighterId,
        boolean champion,
        Instant fetchedAt,
        FightDto.FighterSummary fighter
) {
    public static RankingDto from(Ranking r, Fighter fighter) {
        return new RankingDto(r.getDivision(), r.getRank(), r.getFighterId(), r.isChampion(), r.getFetchedAt(),
                FightDto.FighterSummary.from(fighter));
    }
}
