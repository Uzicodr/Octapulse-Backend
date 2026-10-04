package com.octapulse.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fighters", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "source_id"}))
public class Fighter {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String nickname;

    @Column(name = "record_wins")
    private Integer recordWins;

    @Column(name = "record_losses")
    private Integer recordLosses;

    @Column(name = "record_draws")
    private Integer recordDraws;

    @Column(name = "weight_class")
    private String weightClass;

    @Column(name = "height_inches")
    private String heightInches;

    @Column(name = "reach_inches")
    private String reachInches;

    private String stance;
    private String country;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride = false;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getRecordWins() { return recordWins; }
    public void setRecordWins(Integer recordWins) { this.recordWins = recordWins; }
    public Integer getRecordLosses() { return recordLosses; }
    public void setRecordLosses(Integer recordLosses) { this.recordLosses = recordLosses; }
    public Integer getRecordDraws() { return recordDraws; }
    public void setRecordDraws(Integer recordDraws) { this.recordDraws = recordDraws; }
    public String getWeightClass() { return weightClass; }
    public void setWeightClass(String weightClass) { this.weightClass = weightClass; }
    public String getHeightInches() { return heightInches; }
    public void setHeightInches(String heightInches) { this.heightInches = heightInches; }
    public String getReachInches() { return reachInches; }
    public void setReachInches(String reachInches) { this.reachInches = reachInches; }
    public String getStance() { return stance; }
    public void setStance(String stance) { this.stance = stance; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
