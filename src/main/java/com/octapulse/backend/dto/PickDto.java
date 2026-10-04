package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Pick;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class PickDto {
    private PickDto() {}

    public record PickRequest(@NotNull UUID fightId, @NotNull UUID pickedFighterId) {}

    public record PickResponse(
            UUID id, UUID fightId, UUID pickedFighterId, Instant createdAt,
            Instant lockedAt, Boolean correct
    ) {
        public static PickResponse from(Pick p) {
            return new PickResponse(p.getId(), p.getFightId(), p.getPickedFighterId(),
                    p.getCreatedAt(), p.getLockedAt(), p.getCorrect());
        }
    }
}
