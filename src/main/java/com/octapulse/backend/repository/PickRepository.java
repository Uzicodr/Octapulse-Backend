package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Pick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickRepository extends JpaRepository<Pick, UUID> {
    List<Pick> findByFightId(UUID fightId);

    List<Pick> findByUserId(UUID userId);
}
