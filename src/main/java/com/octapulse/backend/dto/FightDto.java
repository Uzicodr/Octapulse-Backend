package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Fight;

import java.time.Instant;
import java.util.UUID;

public record FightDto(
        UUID id,
        UUID redFighterId,
        UUID blueFighterId,
        String weightClass,
        String cardSection,
        Integer boutOrder,
        boolean titleFight,
        String status,
        UUID winnerFighterId,
        String method,
        Integer resultRound,
        String resultTime,
        Instant startsAt
) {
    public static FightDto from(Fight f) {
        return new FightDto(
                f.getId(), f.getRedFighterId(), f.getBlueFighterId(), f.getWeightClass(),
                f.getCardSection(), f.getBoutOrder(), f.isTitleFight(), f.getStatus(),
                f.getWinnerFighterId(), f.getMethod(), f.getResultRound(), f.getResultTime(),
                f.getStartsAt()
        );
    }
}
