package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Pick;
import com.octapulse.backend.dto.PickDto;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.PickRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PickService {

    private final PickRepository pickRepository;
    private final FightRepository fightRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public PickService(PickRepository pickRepository, FightRepository fightRepository, NamedParameterJdbcTemplate jdbc) {
        this.pickRepository = pickRepository;
        this.fightRepository = fightRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public Pick createOrUpdatePick(UUID userId, PickDto.PickRequest req) {
        Fight fight = fightRepository.findWithEventById(req.fightId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));
        if (PickLock.isLocked(fight)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Picks are locked for this fight");
        }
        UUID pickedFighterId = req.pickedFighterId();
        if (!pickedFighterId.equals(fight.getRedFighterId()) && !pickedFighterId.equals(fight.getBlueFighterId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fighter is not in this fight");
        }
        Integer round = req.round();
        if (round != null) {
            if ("DECISION".equals(req.method())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round can't be picked for a decision");
            }
            if (round > (fight.isTitleFight() ? 5 : 3)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Round is beyond the scheduled rounds");
            }
        }

        Pick pick = pickRepository.findByUserIdAndFightId(userId, req.fightId()).orElseGet(Pick::new);
        pick.setUserId(userId);
        pick.setFightId(req.fightId());
        pick.setPickedFighterId(pickedFighterId);
        pick.setMethod(req.method());
        pick.setRound(round);
        pick.setConfidence(req.confidence() == null ? 1 : req.confidence());
        if (pick.getId() == null) {
            pick.setCreatedAt(Instant.now());
        }
        return pickRepository.save(pick);
    }

    @Transactional
    public void deletePick(UUID userId, UUID fightId) {
        Pick pick = pickRepository.findByUserIdAndFightId(userId, fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pick not found"));
        Fight fight = fightRepository.findWithEventById(fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));
        if (PickLock.isLocked(fight)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Picks are locked for this fight");
        }
        pickRepository.delete(pick);
    }

    public List<Pick> listForUser(UUID userId) {
        return pickRepository.findByUserId(userId);
    }

    /** Another user's picks, limited to fights that have locked so nobody can copy open picks. */
    public List<Pick> listVisibleForUser(UUID userId) {
        List<UUID> ids = jdbc.queryForList("""
                SELECT p.id FROM picks p
                JOIN fights f ON f.id = p.fight_id
                JOIN events e ON e.id = f.event_id
                WHERE p.user_id = :userId AND %s
                """.formatted(PickLock.SQL_LOCKED), new MapSqlParameterSource("userId", userId), UUID.class);
        return pickRepository.findAllById(ids);
    }

    public PickDto.Consensus consensus(UUID fightId) {
        Fight fight = fightRepository.findById(fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));
        var row = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE picked_fighter_id = :red) AS red,
                       COUNT(*) FILTER (WHERE picked_fighter_id = :blue) AS blue,
                       COUNT(*) FILTER (WHERE method = 'KO_TKO') AS ko,
                       COUNT(*) FILTER (WHERE method = 'SUBMISSION') AS sub,
                       COUNT(*) FILTER (WHERE method = 'DECISION') AS dec,
                       COUNT(*) FILTER (WHERE method IS NULL) AS none
                FROM picks p
                JOIN users u ON u.id = p.user_id AND u.role <> 'agent'
                WHERE p.fight_id = :fightId
                """, new MapSqlParameterSource("fightId", fightId)
                .addValue("red", fight.getRedFighterId())
                .addValue("blue", fight.getBlueFighterId()));
        long total = n(row.get("total"));
        long red = n(row.get("red"));
        long blue = n(row.get("blue"));
        return new PickDto.Consensus(
                fightId,
                total,
                new PickDto.Consensus.Side(fight.getRedFighterId(), red, percent(red, total)),
                new PickDto.Consensus.Side(fight.getBlueFighterId(), blue, percent(blue, total)),
                new PickDto.Consensus.MethodSplit(n(row.get("ko")), n(row.get("sub")), n(row.get("dec")),
                        n(row.get("none")))
        );
    }

    private static long n(Object o) {
        return ((Number) o).longValue();
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 1000.0 / total) / 10.0;
    }
}
