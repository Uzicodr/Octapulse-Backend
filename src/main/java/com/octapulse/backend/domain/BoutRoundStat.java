package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bout_round_stats")
public class BoutRoundStat {

    @Id
    private UUID id;

    @Column(name = "fight_id", nullable = false)
    private UUID fightId;

    @Column(name = "fighter_id", nullable = false)
    private UUID fighterId;

    @Column(nullable = false)
    private int round;

    @Column(name = "strikes_landed")
    private Integer strikesLanded;

    @Column(name = "strikes_attempted")
    private Integer strikesAttempted;

    @Column(name = "sig_strikes_landed")
    private Integer sigStrikesLanded;

    @Column(name = "sig_strikes_attempted")
    private Integer sigStrikesAttempted;

    @Column(name = "takedowns_landed")
    private Integer takedownsLanded;

    @Column(name = "takedowns_attempted")
    private Integer takedownsAttempted;

    @Column(name = "control_time_seconds")
    private Integer controlTimeSeconds;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFightId() { return fightId; }
    public UUID getFighterId() { return fighterId; }
    public int getRound() { return round; }
    public Integer getStrikesLanded() { return strikesLanded; }
    public Integer getStrikesAttempted() { return strikesAttempted; }
    public Integer getSigStrikesLanded() { return sigStrikesLanded; }
    public Integer getSigStrikesAttempted() { return sigStrikesAttempted; }
    public Integer getTakedownsLanded() { return takedownsLanded; }
    public Integer getTakedownsAttempted() { return takedownsAttempted; }
    public Integer getControlTimeSeconds() { return controlTimeSeconds; }
    public Instant getFetchedAt() { return fetchedAt; }
}
