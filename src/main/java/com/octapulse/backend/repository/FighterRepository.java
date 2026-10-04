package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Fighter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FighterRepository extends JpaRepository<Fighter, UUID> {
    Optional<Fighter> findBySlug(String slug);

    Page<Fighter> findByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);

    Page<Fighter> findAllByOrderByNameAsc(Pageable pageable);

    Optional<Fighter> findBySourceAndSourceId(String source, String sourceId);
}
