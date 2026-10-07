package com.octapulse.backend.web;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meta")
public class MetaController {

    private final NamedParameterJdbcTemplate jdbc;

    public MetaController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AgentJob(String job, String status, Instant startedAt, Instant finishedAt) {
    }

    /** dataUpdatedAt holds the newest write per table; jobs holds each agent job's latest run. */
    public record LastSync(Map<String, Instant> dataUpdatedAt, List<AgentJob> jobs) {
    }

    @GetMapping("/last-sync")
    public LastSync lastSync() {
        var row = jdbc.queryForMap("""
                SELECT (SELECT MAX(updated_at) FROM events) AS events,
                       (SELECT MAX(updated_at) FROM fights) AS fights,
                       (SELECT MAX(updated_at) FROM fighters) AS fighters,
                       (SELECT MAX(fetched_at) FROM rankings) AS rankings,
                       (SELECT MAX(fetched_at) FROM news_items) AS news
                """, Map.of());
        Map<String, Instant> updated = new LinkedHashMap<>();
        row.forEach((k, v) -> updated.put(k, toInstant(v)));

        List<AgentJob> jobs = jdbc.query("""
                SELECT DISTINCT ON (job) job, status, started_at, finished_at
                FROM agent_runs
                ORDER BY job, started_at DESC
                """, Map.of(), (rs, i) -> new AgentJob(
                rs.getString("job"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at"))));
        return new LastSync(updated, jobs);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        return null;
    }
}
