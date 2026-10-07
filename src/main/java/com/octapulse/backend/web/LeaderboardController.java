package com.octapulse.backend.web;

import com.octapulse.backend.dto.LeaderboardEntry;
import com.octapulse.backend.service.LeaderboardService;
import com.octapulse.backend.service.LeaderboardService.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /** scope: all (default) | month | event (eventId) | following | league (leagueId). */
    @GetMapping
    public List<LeaderboardEntry> leaderboard(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID leagueId,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request
    ) {
        Scope parsed;
        try {
            parsed = Scope.valueOf(scope.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scope: " + scope);
        }
        return leaderboardService.leaderboard(parsed, eventId, leagueId, CurrentUser.optional(request),
                Math.min(Math.max(limit, 1), 500));
    }
}
