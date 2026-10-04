package com.octapulse.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fights", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "source_id"}))
public class Fight {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "red_fighter_id")
    private UUID redFighterId;

    @Column(name = "blue_fighter_id")
    private UUID blueFighterId;

    @Column(name = "weight_class")
    private String weightClass;

    @Column(name = "card_section")
    private String cardSection;

    @Column(name = "bout_order")
    private Integer boutOrder;

    @Column(name = "is_title_fight", nullable = false)
    private boolean titleFight = false;

    @Column(nullable = false)
    private String status;

    @Column(name = "winner_fighter_id")
    private UUID winnerFighterId;

    private String method;

    @Column(name = "result_round")
    private Integer resultRound;

    @Column(name = "result_time")
    private String resultTime;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

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
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public UUID getRedFighterId() { return redFighterId; }
    public void setRedFighterId(UUID redFighterId) { this.redFighterId = redFighterId; }
    public UUID getBlueFighterId() { return blueFighterId; }
    public void setBlueFighterId(UUID blueFighterId) { this.blueFighterId = blueFighterId; }
    public String getWeightClass() { return weightClass; }
    public void setWeightClass(String weightClass) { this.weightClass = weightClass; }
    public String getCardSection() { return cardSection; }
    public void setCardSection(String cardSection) { this.cardSection = cardSection; }
    public Integer getBoutOrder() { return boutOrder; }
    public void setBoutOrder(Integer boutOrder) { this.boutOrder = boutOrder; }
    public boolean isTitleFight() { return titleFight; }
    public void setTitleFight(boolean titleFight) { this.titleFight = titleFight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getWinnerFighterId() { return winnerFighterId; }
    public void setWinnerFighterId(UUID winnerFighterId) { this.winnerFighterId = winnerFighterId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getResultRound() { return resultRound; }
    public void setResultRound(Integer resultRound) { this.resultRound = resultRound; }
    public String getResultTime() { return resultTime; }
    public void setResultTime(String resultTime) { this.resultTime = resultTime; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
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
