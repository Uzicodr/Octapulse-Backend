package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fighter_follows")
@IdClass(FighterFollow.FighterFollowId.class)
public class FighterFollow {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "fighter_id")
    private UUID fighterId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getFighterId() { return fighterId; }
    public void setFighterId(UUID fighterId) { this.fighterId = fighterId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class FighterFollowId implements Serializable {
        private UUID userId;
        private UUID fighterId;

        public FighterFollowId() {}

        public FighterFollowId(UUID userId, UUID fighterId) {
            this.userId = userId;
            this.fighterId = fighterId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FighterFollowId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(fighterId, that.fighterId);
        }

        @Override
        public int hashCode() { return Objects.hash(userId, fighterId); }
    }
}
