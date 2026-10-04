package com.octapulse.backend.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.octapulse.backend.config.CitoProperties;
import com.octapulse.backend.service.UsageBudgetService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class CitoSource implements Source {

    private static final String SOURCE_NAME = "cito";

    private final WebClient client;
    private final CitoProperties props;
    private final UsageBudgetService usageBudgetService;

    public CitoSource(WebClient citoWebClient, CitoProperties props, UsageBudgetService usageBudgetService) {
        this.client = citoWebClient;
        this.props = props;
        this.usageBudgetService = usageBudgetService;
    }

    private JsonNode get(String uri) {
        usageBudgetService.checkAndIncrement(SOURCE_NAME, props.monthlyCap());
        return client.get().uri(uri).retrieve().bodyToMono(JsonNode.class).block();
    }

    @Override
    public JsonNode fetchEvents(int page, int limit) {
        return get("/ufc/events?page=" + page + "&limit=" + limit);
    }

    @Override
    public JsonNode fetchBouts(String eventSlug) {
        return get("/ufc/events/" + eventSlug + "/bouts");
    }

    @Override
    public JsonNode fetchFighter(String slug) {
        return get("/ufc/fighters/" + slug);
    }

    @Override
    public JsonNode fetchFighters(int page, int limit) {
        return get("/ufc/fighters?page=" + page + "&limit=" + limit);
    }

    @Override
    public JsonNode fetchRankings() {
        return get("/ufc/rankings");
    }
}
