package com.octapulse.backend.web;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.User;
import com.octapulse.backend.dto.PickDto.PickResponse;
import com.octapulse.backend.dto.StatsDto;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.PickRepository;
import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.service.StatsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The agent's own picks. The agent writes them as the user with role 'agent', so they are
 * scored and ranked like anyone else's; unlike other users, they're public before lock.
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private final UserRepository userRepository;
    private final PickRepository pickRepository;
    private final FightRepository fightRepository;
    private final StatsService statsService;

    public AiController(UserRepository userRepository, PickRepository pickRepository,
                        FightRepository fightRepository, StatsService statsService) {
        this.userRepository = userRepository;
        this.pickRepository = pickRepository;
        this.fightRepository = fightRepository;
        this.statsService = statsService;
    }

    public record AiProfile(UserDto.Summary user, StatsDto stats) {
    }

    @GetMapping
    public AiProfile profile() {
        User agent = agent();
        return new AiProfile(UserDto.Summary.from(agent), statsService.forUser(agent.getId()));
    }

    @GetMapping("/picks")
    public List<PickResponse> picks(@RequestParam UUID eventId) {
        List<UUID> fightIds = fightRepository.findByEventIdOrderByBoutOrderAsc(eventId).stream()
                .map(Fight::getId).toList();
        if (fightIds.isEmpty()) {
            return List.of();
        }
        return pickRepository.findByUserIdAndFightIdIn(agent().getId(), fightIds).stream()
                .map(PickResponse::from).toList();
    }

    private User agent() {
        return userRepository.findFirstByRole("agent")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI user not configured"));
    }
}
