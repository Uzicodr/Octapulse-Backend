package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Pick;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class PickDto {

    private PickDto() {}

    public record PickRequest(
            @NotNull UUID fightId,
            @NotNull UUID pickedFighterId,
            @Pattern(regexp = "^(KO_TKO|SUBMISSION|DECISION)$") String method,
            @Min(1) @Max(5) Integer round,
            @Min(1) @Max(3) Integer confidence
    ) {}

    public record PickResponse(
            UUID id, UUID fightId, UUID pickedFighterId, Instant createdAt,
            Instant lockedAt, Boolean correct, String method, Integer round,
            int confidence, Integer points, Instant settledAt
    ) {
        public static PickResponse from(Pick p) {
            return new PickResponse(p.getId(), p.getFightId(), p.getPickedFighterId(),
                    p.getCreatedAt(), p.getLockedAt(), p.getCorrect(), p.getMethod(), p.getRound(),
                    p.getConfidence(), p.getPoints(), p.getSettledAt());
        }
    }

    public record Consensus(UUID fightId, long totalPicks, Side red, Side blue, MethodSplit methods) {
        public record Side(UUID fighterId, long picks, double percent) {
        }

        public record MethodSplit(long koTko, long submission, long decision, long unspecified) {
        }
    }
}
