package com.octapulse.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.octapulse.backend.domain.Event;
import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.domain.Ranking;
import com.octapulse.backend.repository.EventRepository;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.FighterRepository;
import com.octapulse.backend.repository.RankingRepository;
import com.octapulse.backend.source.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final String SOURCE_NAME = "cito";

    private final Source source;
    private final EventRepository eventRepository;
    private final FightRepository fightRepository;
    private final FighterRepository fighterRepository;
    private final RankingRepository rankingRepository;
    private final SettlementService settlementService;

    public SyncService(
            Source source,
            EventRepository eventRepository,
            FightRepository fightRepository,
            FighterRepository fighterRepository,
            RankingRepository rankingRepository,
            SettlementService settlementService
    ) {
        this.source = source;
        this.eventRepository = eventRepository;
        this.fightRepository = fightRepository;
        this.fighterRepository = fighterRepository;
        this.rankingRepository = rankingRepository;
        this.settlementService = settlementService;
    }

    @Transactional
    public int syncEventsPage(int page, int limit) {
        JsonNode body = source.fetchEvents(page, limit);
        JsonNode data = body.path("data");
        int count = 0;
        for (JsonNode eventNode : data) {
            upsertEvent(eventNode);
            count++;
        }
        log.info("synced {} events from page {}", count, page);
        return count;
    }

    @Transactional
    public int syncBouts(String eventSlug) {
        Event event = eventRepository.findBySlug(eventSlug).orElse(null);
        if (event == null) {
            log.warn("event {} not found locally, skipping bout sync", eventSlug);
            return 0;
        }
        JsonNode body = source.fetchBouts(eventSlug);
        JsonNode data = body.path("data");
        int count = 0;
        for (JsonNode boutNode : data) {
            upsertFight(boutNode, event);
            count++;
        }
        log.info("synced {} bouts for event {}", count, eventSlug);
        return count;
    }

    @Transactional
    public int syncRankings() {
        JsonNode body = source.fetchRankings();
        JsonNode data = body.path("data");
        rankingRepository.deleteAll();
        int count = 0;
        for (JsonNode rankNode : data) {
            upsertRanking(rankNode);
            count++;
        }
        log.info("synced {} rankings", count);
        return count;
    }

    private Event upsertEvent(JsonNode node) {
        String sourceId = node.path("id").asText();
        Event event = eventRepository.findBySourceAndSourceId(SOURCE_NAME, sourceId).orElseGet(Event::new);
        if (event.isManualOverride()) {
            return event;
        }
        event.setSlug(node.path("slug").asText());
        event.setName(node.path("title").asText());
        event.setStartsAt(parseInstant(node.path("startsAt").asText(null)));
        event.setVenue(textOrNull(node, "venue"));
        event.setCity(textOrNull(node, "city"));
        event.setCountry(textOrNull(node, "country"));
        event.setStatus(node.path("status").asText());
        event.setSource(SOURCE_NAME);
        event.setSourceId(sourceId);
        event.setRawPayload(node.toString());
        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event);
    }

    private Fight upsertFight(JsonNode node, Event event) {
        String sourceId = node.path("id").asText();
        Fight fight = fightRepository.findBySourceAndSourceId(SOURCE_NAME, sourceId).orElseGet(Fight::new);
        if (fight.isManualOverride()) {
            return fight;
        }

        UUID redFighterId = null;
        UUID blueFighterId = null;
        UUID winnerFighterId = null;
        String winnerSlug = node.path("winnerFighterSlug").asText(null);

        for (JsonNode fighterNode : node.path("fighters")) {
            Fighter fighter = upsertFighterStub(fighterNode);
            String corner = fighterNode.path("corner").asText();
            if ("red".equals(corner)) {
                redFighterId = fighter.getId();
            } else if ("blue".equals(corner)) {
                blueFighterId = fighter.getId();
            }
            if (fighter.getSlug().equals(winnerSlug)) {
                winnerFighterId = fighter.getId();
            }
        }

        fight.setEvent(event);
        fight.setRedFighterId(redFighterId);
        fight.setBlueFighterId(blueFighterId);
        fight.setWeightClass(textOrNull(node, "weightClass"));
        fight.setCardSection(textOrNull(node, "cardSection"));
        fight.setBoutOrder(node.hasNonNull("boutOrder") ? node.path("boutOrder").asInt() : null);
        fight.setTitleFight(node.path("titleBout").asBoolean(false));
        fight.setStatus(node.path("status").asText());
        fight.setWinnerFighterId(winnerFighterId);
        fight.setMethod(textOrNull(node, "method"));
        fight.setResultRound(node.hasNonNull("resultRound") ? node.path("resultRound").asInt() : null);
        fight.setResultTime(textOrNull(node, "resultTime"));
        fight.setSource(SOURCE_NAME);
        fight.setSourceId(sourceId);
        fight.setRawPayload(node.toString());
        fight.setUpdatedAt(Instant.now());
        fight = fightRepository.save(fight);

        if ("completed".equals(fight.getStatus()) && fight.getWinnerFighterId() != null) {
            settlementService.settleFight(fight.getId(), fight.getWinnerFighterId());
        }
        return fight;
    }

    private Ranking upsertRanking(JsonNode node) {
        JsonNode fighterNode = node.path("fighter");
        Fighter fighter = upsertFighterStub(fighterNodeOrSelf(node, fighterNode));

        Ranking ranking = new Ranking();
        ranking.setDivision(node.path("division").asText());
        ranking.setRank(node.hasNonNull("rank") ? node.path("rank").asInt() : null);
        ranking.setFighterId(fighter.getId());
        ranking.setChampion(node.path("isChampion").asBoolean(false));
        ranking.setFetchedAt(Instant.now());
        return rankingRepository.save(ranking);
    }

    private JsonNode fighterNodeOrSelf(JsonNode rankNode, JsonNode fighterNode) {
        return fighterNode.isMissingNode() || fighterNode.isNull() ? rankNode : fighterNode;
    }

    private Fighter upsertFighterStub(JsonNode node) {
        String slug = node.hasNonNull("fighterSlug") ? node.path("fighterSlug").asText() : node.path("slug").asText();
        String name = node.hasNonNull("fighterName") ? node.path("fighterName").asText() : node.path("name").asText();
        // The nested "fighter" object inside a ranking entry carries no id/fighterId field at
        // all, so falling through to node.path("id") there yields "" for every fighter and
        // collapses unrelated people onto the same (source, "") row. Fall back to slug (always
        // present, globally unique) instead of ever using a blank sourceId.
        String sourceId = node.hasNonNull("fighterId") ? node.path("fighterId").asText()
                : node.hasNonNull("id") ? node.path("id").asText()
                : slug;

        // Slug is our authoritative identity: Cito's rankings feed has been observed listing
        // the same fighter slug under two different fighterId values, so a sourceId-only
        // lookup can try to steal a slug that another row already owns. Prefer the existing
        // row by slug and let its sourceId get updated to whichever id we saw most recently.
        Fighter fighter = fighterRepository.findBySourceAndSourceId(SOURCE_NAME, sourceId)
                .or(() -> fighterRepository.findBySlug(slug))
                .orElseGet(Fighter::new);
        if (fighter.isManualOverride()) {
            return fighter;
        }
        fighter.setSlug(slug);
        fighter.setName(name);
        if (node.hasNonNull("country")) {
            fighter.setCountry(node.path("country").asText());
        }
        if (node.hasNonNull("division")) {
            fighter.setWeightClass(node.path("division").asText());
        }
        if (node.hasNonNull("nickname")) {
            fighter.setNickname(node.path("nickname").asText(null));
        }
        fighter.setSource(SOURCE_NAME);
        fighter.setSourceId(sourceId);
        fighter.setUpdatedAt(Instant.now());
        return fighterRepository.save(fighter);
    }

    /** Full profile upsert, used when enriching a fighter already known by slug/id. */
    @Transactional
    public Fighter upsertFighterProfile(JsonNode node) {
        String sourceId = node.path("id").asText();
        String slug = node.path("slug").asText();
        Fighter fighter = fighterRepository.findBySourceAndSourceId(SOURCE_NAME, sourceId)
                .or(() -> fighterRepository.findBySlug(slug))
                .orElseGet(Fighter::new);
        if (fighter.isManualOverride()) {
            return fighter;
        }
        fighter.setSlug(slug);
        fighter.setName(node.path("name").asText());
        fighter.setNickname(textOrNull(node, "nickname"));
        fighter.setRecordWins(node.hasNonNull("recordWins") ? node.path("recordWins").asInt() : null);
        fighter.setRecordLosses(node.hasNonNull("recordLosses") ? node.path("recordLosses").asInt() : null);
        fighter.setRecordDraws(node.hasNonNull("recordDraws") ? node.path("recordDraws").asInt() : null);
        fighter.setWeightClass(textOrNull(node, "division"));
        fighter.setHeightInches(textOrNull(node, "heightInches"));
        fighter.setReachInches(textOrNull(node, "reachInches"));
        fighter.setStance(textOrNull(node, "stance"));
        fighter.setCountry(textOrNull(node, "country"));
        fighter.setSource(SOURCE_NAME);
        fighter.setSourceId(sourceId);
        fighter.setRawPayload(node.toString());
        fighter.setUpdatedAt(Instant.now());
        return fighterRepository.save(fighter);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Instant parseInstant(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
