package com.octapulse.backend.repository;

import com.octapulse.backend.domain.LeagueMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, LeagueMember.LeagueMemberId> {
    List<LeagueMember> findByLeagueIdOrderByJoinedAtAsc(UUID leagueId);

    boolean existsByLeagueIdAndUserId(UUID leagueId, UUID userId);

    long countByLeagueId(UUID leagueId);

    long countByUserId(UUID userId);
}
