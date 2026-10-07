package com.octapulse.backend.web;

import com.octapulse.backend.domain.BoutRoundStat;
import com.octapulse.backend.domain.Comment;
import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.dto.FightDto;
import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.dto.PickDto;
import com.octapulse.backend.dto.SocialDto.CommentRequest;
import com.octapulse.backend.dto.SocialDto.CommentResponse;
import com.octapulse.backend.repository.BoutRoundStatRepository;
import com.octapulse.backend.repository.CommentRepository;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.service.Lookups;
import com.octapulse.backend.service.PickService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class FightController {

    private final FightRepository fightRepository;
    private final BoutRoundStatRepository roundStatRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PickService pickService;
    private final Lookups lookups;
    private final NamedParameterJdbcTemplate jdbc;

    public FightController(
            FightRepository fightRepository,
            BoutRoundStatRepository roundStatRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            PickService pickService,
            Lookups lookups,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.fightRepository = fightRepository;
        this.roundStatRepository = roundStatRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.pickService = pickService;
        this.lookups = lookups;
        this.jdbc = jdbc;
    }

    @GetMapping("/fights/{fightId}")
    public FightDto.Detail get(@PathVariable UUID fightId) {
        Fight fight = findFight(fightId);
        var e = fight.getEvent();
        return new FightDto.Detail(
                lookups.fightDtos(List.of(fight)).get(0),
                new FightDto.EventSummary(e.getId(), e.getSlug(), e.getName(), e.getStartsAt(), e.getStatus()),
                commentRepository.countByFightIdAndDeletedAtIsNull(fightId)
        );
    }

    public record RoundStats(int round, UUID fighterId, Integer strikesLanded, Integer strikesAttempted,
                             Integer sigStrikesLanded, Integer sigStrikesAttempted, Integer takedownsLanded,
                             Integer takedownsAttempted, Integer controlTimeSeconds) {
        static RoundStats from(BoutRoundStat s) {
            return new RoundStats(s.getRound(), s.getFighterId(), s.getStrikesLanded(), s.getStrikesAttempted(),
                    s.getSigStrikesLanded(), s.getSigStrikesAttempted(), s.getTakedownsLanded(),
                    s.getTakedownsAttempted(), s.getControlTimeSeconds());
        }
    }

    /** Per-round, per-fighter stats. Empty until the agent has loaded them. */
    @GetMapping("/fights/{fightId}/stats")
    public List<RoundStats> stats(@PathVariable UUID fightId) {
        findFight(fightId);
        return roundStatRepository.findByFightIdOrderByRoundAsc(fightId).stream().map(RoundStats::from).toList();
    }

    @GetMapping("/fights/{fightId}/consensus")
    public PickDto.Consensus consensus(@PathVariable UUID fightId) {
        return pickService.consensus(fightId);
    }

    public record Preview(UUID fightId, String content, String model, Instant generatedAt) {
    }

    @GetMapping("/fights/{fightId}/preview")
    public Preview preview(@PathVariable UUID fightId) {
        List<Preview> rows = jdbc.query(
                "SELECT fight_id, content, model, generated_at FROM fight_previews WHERE fight_id = :fightId",
                new MapSqlParameterSource("fightId", fightId),
                (rs, i) -> new Preview(rs.getObject("fight_id", UUID.class), rs.getString("content"),
                        rs.getString("model"), rs.getTimestamp("generated_at").toInstant()));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No preview for this fight yet");
        }
        return rows.get(0);
    }

    @GetMapping("/fights/{fightId}/comments")
    public PagedResponse<CommentResponse> comments(
            @PathVariable UUID fightId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        limit = CurrentUser.clampLimit(limit);
        var result = commentRepository.findByFightIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                fightId, PageRequest.of(Math.max(page - 1, 0), limit));
        var users = lookups.users(result.getContent().stream().map(Comment::getUserId).toList());
        return PagedResponse.of(result, page, limit, c -> CommentResponse.from(c, users.get(c.getUserId())));
    }

    @PostMapping("/fights/{fightId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable UUID fightId, @Valid @RequestBody CommentRequest req,
                                      HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        if (!fightRepository.existsById(fightId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found");
        }
        Comment comment = new Comment();
        comment.setFightId(fightId);
        comment.setUserId(userId);
        comment.setBody(req.body().strip());
        comment.setCreatedAt(Instant.now());
        comment = commentRepository.save(comment);
        return CommentResponse.from(comment, lookups.users(List.of(userId)).get(userId));
    }

    /** Authors delete their own comments; admins can delete any. */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID commentId, HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        boolean admin = userRepository.findById(userId).map(u -> "admin".equals(u.getRole())).orElse(false);
        if (!comment.getUserId().equals(userId) && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your comment");
        }
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
    }

    private Fight findFight(UUID fightId) {
        return fightRepository.findWithEventById(fightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fight not found"));
    }
}
