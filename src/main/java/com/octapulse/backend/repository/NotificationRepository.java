package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(UUID userId, Instant now);

    /** Returns 1 when inserted, 0 when this user was already notified under the same key. */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, user_id, type, title, body, data, dedupe_key, created_at)
            VALUES (gen_random_uuid(), :userId, :type, :title, :body, CAST(:data AS jsonb), :dedupeKey, now())
            ON CONFLICT (user_id, dedupe_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID userId, String type, String title, String body, String data, String dedupeKey);
}
