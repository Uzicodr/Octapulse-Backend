package com.octapulse.backend.web;

import com.octapulse.backend.dto.LeaderboardEntry;
import com.octapulse.backend.dto.SocialDto.CreateLeagueRequest;
import com.octapulse.backend.dto.SocialDto.JoinLeagueRequest;
import com.octapulse.backend.dto.SocialDto.LeagueDetail;
import com.octapulse.backend.dto.SocialDto.LeagueResponse;
import com.octapulse.backend.service.LeaderboardService;
import com.octapulse.backend.service.LeagueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** All routes require auth (see WebConfig). */
@RestController
@RequestMapping("/leagues")
public class LeagueController {

    private final LeagueService leagueService;
    private final LeaderboardService leaderboardService;

    public LeagueController(LeagueService leagueService, LeaderboardService leaderboardService) {
        this.leagueService = leagueService;
        this.leaderboardService = leaderboardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeagueResponse create(@Valid @RequestBody CreateLeagueRequest req, HttpServletRequest request) {
        return leagueService.create(CurrentUser.require(request), req.name());
    }

    @PostMapping("/join")
    public LeagueResponse join(@Valid @RequestBody JoinLeagueRequest req, HttpServletRequest request) {
        return leagueService.join(CurrentUser.require(request), req.inviteCode());
    }

    @GetMapping("/{leagueId}")
    public LeagueDetail get(@PathVariable UUID leagueId, HttpServletRequest request) {
        return leagueService.detail(CurrentUser.require(request), leagueId);
    }

    @GetMapping("/{leagueId}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable UUID leagueId, HttpServletRequest request) {
        return leaderboardService.leaderboard(LeaderboardService.Scope.LEAGUE, null, leagueId,
                CurrentUser.require(request), LeagueService.MAX_MEMBERS);
    }

    @PostMapping("/{leagueId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID leagueId, HttpServletRequest request) {
        leagueService.leave(CurrentUser.require(request), leagueId);
    }

    @PostMapping("/{leagueId}/invite-code")
    public LeagueResponse regenerateCode(@PathVariable UUID leagueId, HttpServletRequest request) {
        return leagueService.regenerateCode(CurrentUser.require(request), leagueId);
    }

    @DeleteMapping("/{leagueId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID leagueId, @PathVariable UUID userId, HttpServletRequest request) {
        leagueService.removeMember(CurrentUser.require(request), leagueId, userId);
    }

    @DeleteMapping("/{leagueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID leagueId, HttpServletRequest request) {
        leagueService.delete(CurrentUser.require(request), leagueId);
    }
}
