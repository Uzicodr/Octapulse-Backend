package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rankings")
public class Ranking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String division;

    private Integer rank;

    @Column(name = "fighter_id", nullable = false)
    private UUID fighterId;

    @Column(name = "is_champion", nullable = false)
    private boolean champion = false;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public UUID getId() { return id; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public UUID getFighterId() { return fighterId; }
    public void setFighterId(UUID fighterId) { this.fighterId = fighterId; }
    public boolean isChampion() { return champion; }
    public void setChampion(boolean champion) { this.champion = champion; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
