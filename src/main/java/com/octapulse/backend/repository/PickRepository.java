package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Pick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickRepository extends JpaRepository<Pick, UUID> {
    List<Pick> findByFightId(UUID fightId);

    List<Pick> findByUserId(UUID userId);

    Optional<Pick> findByUserIdAndFightId(UUID userId, UUID fightId);

    List<Pick> findByUserIdAndFightIdIn(UUID userId, Collection<UUID> fightIds);
}
