package com.octapulse.backend.service;

import com.octapulse.backend.domain.Pick;
import com.octapulse.backend.repository.PickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PickRepository pickRepository;

    public SettlementService(PickRepository pickRepository) {
        this.pickRepository = pickRepository;
    }

    @Transactional
    public void settleFight(UUID fightId, UUID winnerFighterId) {
        var picks = pickRepository.findByFightId(fightId);
        for (Pick pick : picks) {
            pick.setCorrect(pick.getPickedFighterId().equals(winnerFighterId));
        }
        pickRepository.saveAll(picks);
        log.info("settled {} picks for fight {}", picks.size(), fightId);
    }
}
