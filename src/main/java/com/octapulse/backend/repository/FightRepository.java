package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Fight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FightRepository extends JpaRepository<Fight, UUID> {
    // The event is fetched eagerly because lock checks read its start time outside a transaction.
    @Query("SELECT f FROM Fight f JOIN FETCH f.event WHERE f.event.id = :eventId ORDER BY f.boutOrder ASC")
    List<Fight> findByEventIdOrderByBoutOrderAsc(UUID eventId);

    @Query("SELECT f FROM Fight f JOIN FETCH f.event WHERE f.id = :id")
    Optional<Fight> findWithEventById(UUID id);

    @Query("SELECT f FROM Fight f JOIN FETCH f.event WHERE f.id IN :ids")
    List<Fight> findWithEventByIdIn(Collection<UUID> ids);

    Optional<Fight> findBySourceAndSourceId(String source, String sourceId);

    /** Fights with a result (or a voiding status) that still have unsettled picks. */
    @Query("""
            SELECT f FROM Fight f
            WHERE (f.winnerFighterId IS NOT NULL OR LOWER(f.status) IN :voidStatuses)
              AND EXISTS (SELECT 1 FROM Pick p WHERE p.fightId = f.id AND p.settledAt IS NULL)
            """)
    List<Fight> findSettleable(Collection<String> voidStatuses);

    @Query("""
            SELECT f FROM Fight f JOIN FETCH f.event e
            WHERE f.redFighterId = :fighterId OR f.blueFighterId = :fighterId
            ORDER BY COALESCE(f.startsAt, e.startsAt) DESC
            """)
    List<Fight> findByFighter(UUID fighterId);
}
