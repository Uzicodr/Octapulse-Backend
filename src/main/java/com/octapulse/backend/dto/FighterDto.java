package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Fighter;

import java.util.List;
import java.util.UUID;

public record FighterDto(
        UUID id,
        String slug,
        String name,
        String nickname,
        Integer recordWins,
        Integer recordLosses,
        Integer recordDraws,
        String weightClass,
        String heightInches,
        String reachInches,
        String stance,
        String country
) {
    public static FighterDto from(Fighter f) {
        return new FighterDto(
                f.getId(), f.getSlug(), f.getName(), f.getNickname(),
                f.getRecordWins(), f.getRecordLosses(), f.getRecordDraws(),
                f.getWeightClass(), f.getHeightInches(), f.getReachInches(),
                f.getStance(), f.getCountry()
        );
    }

    public record ListResponse(List<FighterDto> data, PageDto meta) {
    }
}
