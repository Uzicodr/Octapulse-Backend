package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.service.PickLock;

import java.time.Instant;
import java.util.Map;
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
        Instant startsAt,
        FighterSummary redFighter,
        FighterSummary blueFighter,
        boolean locked
) {
    /** fighters may be empty; the summaries are then null and clients fall back to the ids. */
    public static FightDto from(Fight f, Map<UUID, Fighter> fighters) {
        return new FightDto(
                f.getId(), f.getRedFighterId(), f.getBlueFighterId(), f.getWeightClass(),
                f.getCardSection(), f.getBoutOrder(), f.isTitleFight(), f.getStatus(),
                f.getWinnerFighterId(), f.getMethod(), f.getResultRound(), f.getResultTime(),
                f.getStartsAt(),
                FighterSummary.from(fighters.get(f.getRedFighterId())),
                FighterSummary.from(fighters.get(f.getBlueFighterId())),
                PickLock.isLocked(f)
        );
    }

    public record FighterSummary(UUID id, String slug, String name, String nickname, String country,
                                 Integer recordWins, Integer recordLosses, Integer recordDraws) {
        public static FighterSummary from(Fighter f) {
            if (f == null) {
                return null;
            }
            return new FighterSummary(f.getId(), f.getSlug(), f.getName(), f.getNickname(), f.getCountry(),
                    f.getRecordWins(), f.getRecordLosses(), f.getRecordDraws());
        }
    }

    public record EventSummary(UUID id, String slug, String name, Instant startsAt, String status) {
    }

    public record Detail(FightDto fight, EventSummary event, long commentCount) {
    }
}
