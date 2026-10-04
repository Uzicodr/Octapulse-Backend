package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Fight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FightRepository extends JpaRepository<Fight, UUID> {
    List<Fight> findByEventIdOrderByBoutOrderAsc(UUID eventId);

    Optional<Fight> findBySourceAndSourceId(String source, String sourceId);
}
