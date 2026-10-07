package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.dto.NewsDto;
import com.octapulse.backend.dto.NewsDto.FighterRef;
import com.octapulse.backend.dto.NewsDto.ImageCredit;
import com.octapulse.backend.dto.PagedResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads the news_items table that the agent's sync_news job fills. */
@Service
public class NewsService {

    public static final Set<String> KINDS = Set.of("announcement", "result", "injury", "rumor", "news");

    private final NamedParameterJdbcTemplate jdbc;
    private final Lookups lookups;

    public NewsService(NamedParameterJdbcTemplate jdbc, Lookups lookups) {
        this.jdbc = jdbc;
        this.lookups = lookups;
    }

    /** Newest first. kind and fighterId are optional filters. */
    public PagedResponse<NewsDto> list(String kind, UUID fighterId, int page, int limit) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", (long) (Math.max(page, 1) - 1) * limit);
        if (kind != null) {
            where.append(" AND n.kind = :kind");
            params.addValue("kind", kind);
        }
        if (fighterId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM news_item_fighters nf"
                    + " WHERE nf.news_id = n.id AND nf.fighter_id = :fighterId)");
            params.addValue("fighterId", fighterId);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM news_items n" + where, params, Long.class);
        List<Row> rows = jdbc.query("""
                SELECT n.id, n.source, n.source_name, n.title, n.summary, n.url, n.image_url, n.image_credit,
                       n.image_license, n.image_credit_url, n.kind, n.published_at
                FROM news_items n""" + where + " ORDER BY n.published_at DESC, n.id LIMIT :limit OFFSET :offset",
                params,
                (rs, i) -> new Row(
                        rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("source_name"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("url"),
                        rs.getString("image_url"),
                        rs.getString("image_credit") == null ? null : new ImageCredit(rs.getString("image_credit"),
                                rs.getString("image_license"), rs.getString("image_credit_url")),
                        rs.getString("kind"),
                        rs.getTimestamp("published_at").toInstant()
                ));

        Map<UUID, List<FighterRef>> tags = fighterTags(rows.stream().map(Row::id).toList());
        List<NewsDto> data = rows.stream().map(r -> new NewsDto(
                r.id(), r.source(), r.sourceName(), r.title(), r.summary(), r.url(), r.imageUrl(), r.imageCredit(), r.kind(),
                r.publishedAt(), tags.getOrDefault(r.id(), List.of())
        )).toList();
        return PagedResponse.of(data, page, limit, total == null ? 0 : total);
    }

    private Map<UUID, List<FighterRef>> fighterTags(List<UUID> newsIds) {
        if (newsIds.isEmpty()) {
            return Map.of();
        }
        List<UUID[]> pairs = jdbc.query(
                "SELECT news_id, fighter_id FROM news_item_fighters WHERE news_id IN (:ids)",
                Map.of("ids", newsIds),
                (rs, i) -> new UUID[]{rs.getObject("news_id", UUID.class), rs.getObject("fighter_id", UUID.class)});
        Map<UUID, Fighter> fighters = lookups.fighters(pairs.stream().map(p -> p[1]).toList());
        Map<UUID, List<FighterRef>> tags = new HashMap<>();
        for (UUID[] pair : pairs) {
            Fighter f = fighters.get(pair[1]);
            if (f != null) {
                tags.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(new FighterRef(f.getId(), f.getSlug(), f.getName()));
            }
        }
        tags.values().forEach(list -> list.sort(Comparator.comparing(FighterRef::name, Comparator.nullsLast(Comparator.naturalOrder()))));
        return tags;
    }

    private record Row(UUID id, String source, String sourceName, String title, String summary, String url,
                       String imageUrl, ImageCredit imageCredit, String kind, Instant publishedAt) {
    }
}
