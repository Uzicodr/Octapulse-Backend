package com.octapulse.backend.repository;

import com.octapulse.backend.domain.BoutRoundStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BoutRoundStatRepository extends JpaRepository<BoutRoundStat, UUID> {
    List<BoutRoundStat> findByFightIdOrderByRoundAsc(UUID fightId);
}
