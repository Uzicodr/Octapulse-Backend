package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Pick;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.PickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Scores picks once the agent writes a fight result.
 *
 * <p>A correct winner earns the pick's confidence (1-3), plus 1 for the right method and,
 * when that method is a finish, plus 1 more for the right round. Wrong winner earns 0. Fights with a voiding
 * status and no winner (draw, no contest, cancelled) void their picks: is_correct stays
 * null and they are left out of accuracy and leaderboards.
 */
@Service
public class SettlementService {

    public static final Set<String> VOID_STATUSES = Set.of("draw", "no_contest", "nc", "cancelled", "canceled");

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PickRepository pickRepository;
    private final FightRepository fightRepository;

    public SettlementService(PickRepository pickRepository, FightRepository fightRepository) {
        this.pickRepository = pickRepository;
        this.fightRepository = fightRepository;
    }

    /** Fights and events touched by a settlement run, for follow-up notifications. */
    public record Settled(List<UUID> fightIds, Set<UUID> eventIds, int picks) {
    }

    /** Settles every fight that has a result but unsettled picks. */
    @Transactional
    public Settled settlePending() {
        List<Fight> fights = fightRepository.findSettleable(VOID_STATUSES);
        List<UUID> fightIds = new ArrayList<>();
        Set<UUID> eventIds = new LinkedHashSet<>();
        int picks = 0;
        for (Fight fight : fights) {
            picks += settle(fight);
            fightIds.add(fight.getId());
            eventIds.add(fight.getEvent().getId());
        }
        if (!fights.isEmpty()) {
            log.info("settled {} fights across {} events", fights.size(), eventIds.size());
        }
        return new Settled(fightIds, eventIds, picks);
    }

    /** Re-scores all picks on one fight, e.g. after an admin corrects a result. */
    @Transactional
    public Settled settleFight(UUID fightId) {
        Fight fight = fightRepository.findWithEventById(fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));
        if (fight.getWinnerFighterId() == null && !isVoid(fight)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fight has no result yet");
        }
        int picks = settle(fight);
        return new Settled(List.of(fight.getId()), Set.of(fight.getEvent().getId()), picks);
    }

    private int settle(Fight fight) {
        List<Pick> picks = pickRepository.findByFightId(fight.getId());
        Instant now = Instant.now();
        boolean isVoid = fight.getWinnerFighterId() == null;
        PickMethod resultMethod = PickMethod.fromResult(fight.getMethod());
        for (Pick pick : picks) {
            if (isVoid) {
                pick.setCorrect(null);
                pick.setPoints(0);
            } else {
                boolean correct = pick.getPickedFighterId().equals(fight.getWinnerFighterId());
                pick.setCorrect(correct);
                pick.setPoints(correct ? score(pick, resultMethod, fight.getResultRound()) : 0);
            }
            pick.setSettledAt(now);
        }
        pickRepository.saveAll(picks);
        log.info("settled {} picks for fight {}", picks.size(), fight.getId());
        return picks.size();
    }

    static int score(Pick pick, PickMethod resultMethod, Integer resultRound) {
        int points = pick.getConfidence();
        if (pick.getMethod() != null && resultMethod != null && pick.getMethod().equals(resultMethod.name())) {
            points++;
            if (resultMethod != PickMethod.DECISION && pick.getRound() != null && pick.getRound().equals(resultRound)) {
                points++;
            }
        }
        return points;
    }

    private static boolean isVoid(Fight fight) {
        return fight.getStatus() != null && VOID_STATUSES.contains(fight.getStatus().toLowerCase(Locale.ROOT));
    }
}
