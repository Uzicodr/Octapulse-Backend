package com.octapulse.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One headline from a publisher feed. Title and summary are exactly what the feed gave us; show them
 * unchanged, credit sourceName, and open url for the full story.
 */
public record NewsDto(
        UUID id,
        String source,
        String sourceName,
        String title,
        String summary,
        String url,
        String imageUrl,
        String kind,
        Instant publishedAt,
        List<FighterRef> fighters
) {
    public record FighterRef(UUID id, String slug, String name) {
    }
}
