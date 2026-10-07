package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDto(
        UUID id,
        String slug,
        String name,
        Instant startsAt,
        String venue,
        String city,
        String country,
        String status
) {
    public static EventDto from(Event e) {
        return new EventDto(e.getId(), e.getSlug(), e.getName(), e.getStartsAt(),
                e.getVenue(), e.getCity(), e.getCountry(), e.getStatus());
    }

    public record ListResponse(List<EventDto> data, PageDto meta) {
    }

    public record Detail(
            UUID id, String slug, String name, Instant startsAt, String venue,
            String city, String country, String status, List<FightDto> fights
    ) {
        public static Detail from(Event e, List<FightDto> fights) {
            return new Detail(
                    e.getId(), e.getSlug(), e.getName(), e.getStartsAt(), e.getVenue(),
                    e.getCity(), e.getCountry(), e.getStatus(),
                    fights
            );
        }
    }
}
