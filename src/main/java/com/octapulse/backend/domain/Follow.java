package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "follows")
@IdClass(Follow.FollowId.class)
public class Follow {

    @Id
    @Column(name = "follower_id")
    private UUID followerId;

    @Id
    @Column(name = "followee_id")
    private UUID followeeId;

    public UUID getFollowerId() { return followerId; }
    public void setFollowerId(UUID followerId) { this.followerId = followerId; }
    public UUID getFolloweeId() { return followeeId; }
    public void setFolloweeId(UUID followeeId) { this.followeeId = followeeId; }

    public static class FollowId implements Serializable {
        private UUID followerId;
        private UUID followeeId;

        public FollowId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FollowId that)) return false;
            return Objects.equals(followerId, that.followerId) && Objects.equals(followeeId, that.followeeId);
        }

        @Override
        public int hashCode() { return Objects.hash(followerId, followeeId); }
    }
}
