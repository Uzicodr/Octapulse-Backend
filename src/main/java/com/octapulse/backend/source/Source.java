package com.octapulse.backend.source;

import com.fasterxml.jackson.databind.JsonNode;

public interface Source {
    JsonNode fetchEvents(int page, int limit);

    JsonNode fetchBouts(String eventSlug);

    JsonNode fetchFighter(String slug);

    JsonNode fetchFighters(int page, int limit);

    JsonNode fetchRankings();
}
