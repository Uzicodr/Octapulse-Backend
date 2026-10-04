package com.octapulse.backend.dto;

import com.octapulse.backend.domain.Event;
import com.octapulse.backend.domain.Fight;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        public static Detail from(Event e, List<Fight> fights) {
            return new Detail(
                    e.getId(), e.getSlug(), e.getName(), e.getStartsAt(), e.getVenue(),
                    e.getCity(), e.getCountry(), e.getStatus(),
                    fights.stream().map(FightDto::from).collect(Collectors.toList())
            );
        }
    }
}
