package com.octapulse.backend.repository;

import com.octapulse.backend.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Page<Comment> findByFightIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID fightId, Pageable pageable);

    long countByFightIdAndDeletedAtIsNull(UUID fightId);
}
