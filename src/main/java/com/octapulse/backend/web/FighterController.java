package com.octapulse.backend.web;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.domain.FighterFollow;
import com.octapulse.backend.dto.FightDto;
import com.octapulse.backend.dto.FighterDto;
import com.octapulse.backend.dto.PageDto;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.FighterFollowRepository;
import com.octapulse.backend.repository.FighterRepository;
import com.octapulse.backend.service.Lookups;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/fighters")
public class FighterController {

    private final FighterRepository fighterRepository;
    private final FightRepository fightRepository;
    private final FighterFollowRepository fighterFollowRepository;
    private final Lookups lookups;

    public FighterController(
            FighterRepository fighterRepository,
            FightRepository fightRepository,
            FighterFollowRepository fighterFollowRepository,
            Lookups lookups
    ) {
        this.fighterRepository = fighterRepository;
        this.fightRepository = fightRepository;
        this.fighterFollowRepository = fighterFollowRepository;
        this.lookups = lookups;
    }

    @GetMapping
    public FighterDto.ListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        limit = CurrentUser.clampLimit(limit);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<Fighter> result = (q == null || q.isBlank())
                ? fighterRepository.findAllByOrderByNameAsc(pageable)
                : fighterRepository.findByNameContainingIgnoreCaseOrderByNameAsc(q, pageable);
        List<FighterDto> data = result.getContent().stream().map(FighterDto::from).collect(Collectors.toList());
        PageDto meta = new PageDto(page, limit, result.getTotalElements(), result.getTotalPages());
        return new FighterDto.ListResponse(data, meta);
    }

    @GetMapping("/{slug}")
    public FighterDto get(@PathVariable String slug) {
        return FighterDto.from(findFighter(slug));
    }

    /** Fight history and upcoming bookings, newest first. */
    @GetMapping("/{slug}/fights")
    public List<FightDto> fights(@PathVariable String slug) {
        List<Fight> fights = fightRepository.findByFighter(findFighter(slug).getId());
        return lookups.fightDtos(fights);
    }

    public record FollowState(UUID fighterId, boolean following, long followers) {
    }

    @PostMapping("/{slug}/follow")
    public FollowState follow(@PathVariable String slug, HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        Fighter fighter = findFighter(slug);
        if (!fighterFollowRepository.existsByUserIdAndFighterId(userId, fighter.getId())) {
            FighterFollow follow = new FighterFollow();
            follow.setUserId(userId);
            follow.setFighterId(fighter.getId());
            follow.setCreatedAt(Instant.now());
            fighterFollowRepository.save(follow);
        }
        return new FollowState(fighter.getId(), true, fighterFollowRepository.countByFighterId(fighter.getId()));
    }

    @DeleteMapping("/{slug}/follow")
    public FollowState unfollow(@PathVariable String slug, HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        Fighter fighter = findFighter(slug);
        fighterFollowRepository.deleteById(new FighterFollow.FighterFollowId(userId, fighter.getId()));
        return new FollowState(fighter.getId(), false, fighterFollowRepository.countByFighterId(fighter.getId()));
    }

    private Fighter findFighter(String slug) {
        return fighterRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fighter not found"));
    }
}
