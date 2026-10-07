package com.octapulse.backend.repository;

import com.octapulse.backend.domain.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {
    Optional<League> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    @Query("SELECT l FROM League l WHERE l.id IN (SELECT m.leagueId FROM LeagueMember m WHERE m.userId = :userId) ORDER BY l.createdAt DESC")
    List<League> findForMember(UUID userId);
}
