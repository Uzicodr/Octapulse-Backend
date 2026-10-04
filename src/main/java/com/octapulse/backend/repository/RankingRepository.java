package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Ranking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RankingRepository extends JpaRepository<Ranking, UUID> {
    List<Ranking> findByDivisionOrderByRankAsc(String division);

    List<Ranking> findAllByOrderByDivisionAscRankAsc();

    void deleteByDivision(String division);
}
