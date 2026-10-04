package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Pick;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.PickRepository;
import org.springframework.http.HttpStatus;
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

    public PickService(PickRepository pickRepository, FightRepository fightRepository) {
        this.pickRepository = pickRepository;
        this.fightRepository = fightRepository;
    }

    @Transactional
    public Pick createOrUpdatePick(UUID userId, UUID fightId, UUID pickedFighterId) {
        Fight fight = fightRepository.findById(fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));

        if (isLocked(fight)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Picks are locked for this fight");
        }
        if (!pickedFighterId.equals(fight.getRedFighterId()) && !pickedFighterId.equals(fight.getBlueFighterId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fighter is not in this fight");
        }

        Pick pick = pickRepository.findByUserId(userId).stream()
                .filter(p -> p.getFightId().equals(fightId))
                .findFirst()
                .orElseGet(Pick::new);

        pick.setUserId(userId);
        pick.setFightId(fightId);
        pick.setPickedFighterId(pickedFighterId);
        if (pick.getId() == null) {
            pick.setCreatedAt(Instant.now());
        }
        return pickRepository.save(pick);
    }

    public List<Pick> listForUser(UUID userId) {
        return pickRepository.findByUserId(userId);
    }

    private boolean isLocked(Fight fight) {
        Instant lockAt = fight.getStartsAt() != null ? fight.getStartsAt() : fight.getEvent().getStartsAt();
        return lockAt != null && !lockAt.isAfter(Instant.now());
    }
}
