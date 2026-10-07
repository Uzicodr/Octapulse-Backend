package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "picks", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "fight_id"}))
public class Pick {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fight_id", nullable = false)
    private UUID fightId;

    @Column(name = "picked_fighter_id", nullable = false)
    private UUID pickedFighterId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "is_correct")
    private Boolean correct;

    private String method;

    private Integer round;

    @Column(nullable = false)
    private int confidence = 1;

    private Integer points;

    @Column(name = "settled_at")
    private Instant settledAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getFightId() { return fightId; }
    public void setFightId(UUID fightId) { this.fightId = fightId; }
    public UUID getPickedFighterId() { return pickedFighterId; }
    public void setPickedFighterId(UUID pickedFighterId) { this.pickedFighterId = pickedFighterId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
