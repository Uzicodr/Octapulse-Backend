package com.octapulse.backend.repository;

import com.octapulse.backend.domain.FighterFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FighterFollowRepository extends JpaRepository<FighterFollow, FighterFollow.FighterFollowId> {
    List<FighterFollow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndFighterId(UUID userId, UUID fighterId);

    long countByFighterId(UUID fighterId);
}
