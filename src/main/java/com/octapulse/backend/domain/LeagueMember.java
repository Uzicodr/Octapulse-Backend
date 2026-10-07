package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "league_members")
@IdClass(LeagueMember.LeagueMemberId.class)
public class LeagueMember {

    @Id
    @Column(name = "league_id")
    private UUID leagueId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public UUID getLeagueId() { return leagueId; }
    public void setLeagueId(UUID leagueId) { this.leagueId = leagueId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public static class LeagueMemberId implements Serializable {
        private UUID leagueId;
        private UUID userId;

        public LeagueMemberId() {}

        public LeagueMemberId(UUID leagueId, UUID userId) {
            this.leagueId = leagueId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LeagueMemberId that)) return false;
            return Objects.equals(leagueId, that.leagueId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() { return Objects.hash(leagueId, userId); }
    }
}
