package com.octapulse.backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapulse.backend.domain.User;
import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.service.SettlementService;
import com.octapulse.backend.service.notify.NotificationTriggers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Requires role 'admin' (see AdminInterceptor). Agent tables are read with SQL since the agent owns their shape. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final NamedParameterJdbcTemplate jdbc;
    private final SettlementService settlementService;
    private final NotificationTriggers triggers;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminController(
            NamedParameterJdbcTemplate jdbc,
            SettlementService settlementService,
            NotificationTriggers triggers,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.settlementService = settlementService;
        this.triggers = triggers;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public record AgentRun(UUID id, String job, String status, JsonNode input, JsonNode summary, String error,
                           String provider, String model, int inputTokens, int outputTokens,
                           Instant startedAt, Instant finishedAt) {
    }

    public record AgentStep(UUID id, int step, String kind, String name, JsonNode args, JsonNode result,
                            boolean isError, String provider, String model, Integer inputTokens,
                            Integer outputTokens, Integer durationMs, Instant createdAt) {
    }

    public record AgentRunDetail(AgentRun run, List<AgentStep> steps) {
    }

    @GetMapping("/agent-runs")
    public PagedResponse<AgentRun> agentRuns(
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        limit = CurrentUser.clampLimit(limit);
        var params = new MapSqlParameterSource("job", job)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", (long) Math.max(page - 1, 0) * limit);
        String where = " WHERE (CAST(:job AS varchar) IS NULL OR job = :job)"
                + " AND (CAST(:status AS varchar) IS NULL OR status = :status)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM agent_runs" + where, params, Long.class);
        List<AgentRun> runs = jdbc.query("SELECT * FROM agent_runs" + where
                + " ORDER BY started_at DESC LIMIT :limit OFFSET :offset", params, (rs, i) -> run(rs));
        return PagedResponse.of(runs, page, limit, total == null ? 0 : total);
    }

    @GetMapping("/agent-runs/{runId}")
    public AgentRunDetail agentRun(@PathVariable UUID runId) {
        var params = new MapSqlParameterSource("runId", runId);
        List<AgentRun> runs = jdbc.query("SELECT * FROM agent_runs WHERE id = :runId", params, (rs, i) -> run(rs));
        if (runs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
        }
        List<AgentStep> steps = jdbc.query("SELECT * FROM agent_steps WHERE run_id = :runId ORDER BY step",
                params, (rs, i) -> new AgentStep(
                        rs.getObject("id", UUID.class), rs.getInt("step"), rs.getString("kind"), rs.getString("name"),
                        json(rs.getString("args")), json(rs.getString("result")), rs.getBoolean("is_error"),
                        rs.getString("provider"), rs.getString("model"),
                        rs.getObject("input_tokens", Integer.class), rs.getObject("output_tokens", Integer.class),
                        rs.getObject("duration_ms", Integer.class), instant(rs.getTimestamp("created_at"))));
        return new AgentRunDetail(runs.get(0), steps);
    }

    public record ReviewItem(UUID id, UUID runId, String entityType, UUID entityId, String reason, JsonNode payload,
                             String status, Instant createdAt, Instant resolvedAt) {
    }

    @GetMapping("/review-queue")
    public PagedResponse<ReviewItem> reviewQueue(
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        limit = CurrentUser.clampLimit(limit);
        var params = new MapSqlParameterSource("status", status)
                .addValue("limit", limit)
                .addValue("offset", (long) Math.max(page - 1, 0) * limit);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM review_queue WHERE status = :status", params, Long.class);
        List<ReviewItem> items = jdbc.query("""
                SELECT * FROM review_queue WHERE status = :status
                ORDER BY created_at ASC LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> new ReviewItem(
                rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getString("reason"), json(rs.getString("payload")),
                rs.getString("status"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("resolved_at"))));
        return PagedResponse.of(items, page, limit, total == null ? 0 : total);
    }

    public record ResolveRequest(@NotBlank @Pattern(regexp = "^(resolved|dismissed)$") String status) {
    }

    @PostMapping("/review-queue/{itemId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable UUID itemId, @Valid @RequestBody ResolveRequest req) {
        int updated = jdbc.update("""
                UPDATE review_queue SET status = :status, resolved_at = now()
                WHERE id = :id AND status = 'open'
                """, new MapSqlParameterSource("status", req.status()).addValue("id", itemId));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No open review item with that id");
        }
    }

    public record SettleResult(int fightsSettled, int picksSettled) {
    }

    /** Settles everything pending now instead of waiting for the next job run. */
    @PostMapping("/settlement/run")
    public SettleResult runSettlement() {
        var settled = settlementService.settlePending();
        notifyAfter(settled);
        return new SettleResult(settled.fightIds().size(), settled.picks());
    }

    /** Re-scores one fight, e.g. after its result was corrected. */
    @PostMapping("/fights/{fightId}/settle")
    public SettleResult settleFight(@PathVariable UUID fightId) {
        var settled = settlementService.settleFight(fightId);
        notifyAfter(settled);
        return new SettleResult(1, settled.picks());
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "^(user|admin)$") String role) {
    }

    @PostMapping("/users/{userId}/role")
    public UserDto.Me setRole(@PathVariable UUID userId, @Valid @RequestBody RoleRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if ("agent".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The AI user's role can't be changed");
        }
        user.setRole(req.role());
        return UserDto.Me.from(userRepository.save(user));
    }

    private void notifyAfter(SettlementService.Settled settled) {
        triggers.fightResults(settled.fightIds());
        settled.eventIds().forEach(triggers::eventSettled);
    }

    private AgentRun run(ResultSet rs) throws SQLException {
        return new AgentRun(
                rs.getObject("id", UUID.class), rs.getString("job"), rs.getString("status"),
                json(rs.getString("input")), json(rs.getString("summary")), rs.getString("error"),
                rs.getString("provider"), rs.getString("model"),
                rs.getInt("input_tokens"), rs.getInt("output_tokens"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")));
    }

    private JsonNode json(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(raw);
        }
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
