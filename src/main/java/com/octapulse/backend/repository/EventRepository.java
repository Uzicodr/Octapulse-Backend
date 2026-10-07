package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findBySlug(String slug);

    Optional<Event> findBySourceAndSourceId(String source, String sourceId);

    Page<Event> findByStatusInOrderByStartsAtAsc(List<String> statuses, Pageable pageable);

    Page<Event> findByStatusOrderByStartsAtDesc(String status, Pageable pageable);

    Page<Event> findAllByOrderByStartsAtDesc(Pageable pageable);

    List<Event> findByStartsAtBetween(Instant from, Instant to);
}
