package com.octapulse.backend.web;

import com.octapulse.backend.dto.LeaderboardEntry;
import com.octapulse.backend.repository.LeaderboardRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final LeaderboardRepository leaderboardRepository;

    public LeaderboardController(LeaderboardRepository leaderboardRepository) {
        this.leaderboardRepository = leaderboardRepository;
    }

    @GetMapping
    public List<LeaderboardEntry> leaderboard() {
        return leaderboardRepository.fetchLeaderboard().stream()
                .map(row -> new LeaderboardEntry(
                        row.getUserId(),
                        row.getUsername(),
                        row.getCorrectPicks(),
                        row.getSettledPicks(),
                        row.getSettledPicks() == 0 ? 0.0 : (double) row.getCorrectPicks() / row.getSettledPicks()
                ))
                .collect(Collectors.toList());
    }
}
