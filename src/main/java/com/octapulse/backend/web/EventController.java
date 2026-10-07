package com.octapulse.backend.web;

import com.octapulse.backend.domain.Event;
import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.dto.EventDto;
import com.octapulse.backend.dto.PageDto;
import com.octapulse.backend.repository.EventRepository;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.service.Lookups;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventRepository eventRepository;
    private final FightRepository fightRepository;
    private final Lookups lookups;

    public EventController(EventRepository eventRepository, FightRepository fightRepository, Lookups lookups) {
        this.eventRepository = eventRepository;
        this.fightRepository = fightRepository;
        this.lookups = lookups;
    }

    @GetMapping
    public EventDto.ListResponse list(
            @RequestParam(required = false) String when,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        limit = CurrentUser.clampLimit(limit);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<Event> result;
        if ("upcoming".equals(when)) {
            result = eventRepository.findByStatusInOrderByStartsAtAsc(List.of("scheduled", "live"), pageable);
        } else if ("past".equals(when)) {
            result = eventRepository.findByStatusOrderByStartsAtDesc("completed", pageable);
        } else {
            result = eventRepository.findAllByOrderByStartsAtDesc(pageable);
        }
        List<EventDto> data = result.getContent().stream().map(EventDto::from).collect(Collectors.toList());
        PageDto meta = new PageDto(page, limit, result.getTotalElements(), result.getTotalPages());
        return new EventDto.ListResponse(data, meta);
    }

    @GetMapping("/{eventId}")
    public EventDto.Detail get(@PathVariable UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        List<Fight> fights = fightRepository.findByEventIdOrderByBoutOrderAsc(eventId);
        return EventDto.Detail.from(event, lookups.fightDtos(fights));
    }
}
