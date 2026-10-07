package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Follow;
import com.octapulse.backend.domain.Pick;
import com.octapulse.backend.domain.User;
import com.octapulse.backend.dto.FightDto;
import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.dto.PickDto;
import com.octapulse.backend.dto.SocialDto.FeedItem;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.FightRepository;
import com.octapulse.backend.repository.FollowRepository;
import com.octapulse.backend.repository.PickRepository;
import com.octapulse.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SocialService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PickRepository pickRepository;
    private final FightRepository fightRepository;
    private final StatsService statsService;
    private final Lookups lookups;
    private final NamedParameterJdbcTemplate jdbc;

    public SocialService(
            UserRepository userRepository,
            FollowRepository followRepository,
            PickRepository pickRepository,
            FightRepository fightRepository,
            StatsService statsService,
            Lookups lookups,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.pickRepository = pickRepository;
        this.fightRepository = fightRepository;
        this.statsService = statsService;
        this.lookups = lookups;
        this.jdbc = jdbc;
    }

    @Transactional
    public User updateMe(UUID userId, UserDto.UpdateMeRequest req) {
        User user = findUser(userId);
        if (req.username() != null) {
            if (!req.username().equalsIgnoreCase(user.getUsername())
                    && userRepository.existsByUsernameIgnoreCase(req.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
            }
            user.setUsername(req.username());
        }
        if (req.displayName() != null) {
            user.setDisplayName(blankToNull(req.displayName()));
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(blankToNull(req.avatarUrl()));
        }
        if (req.bio() != null) {
            user.setBio(blankToNull(req.bio()));
        }
        return userRepository.save(user);
    }

    public UserDto.Profile profile(UUID userId, UUID viewerId) {
        User user = findUser(userId);
        Boolean followedByMe = viewerId == null ? null
                : followRepository.existsByFollowerIdAndFolloweeId(viewerId, userId);
        return new UserDto.Profile(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl(),
                user.getBio(), "agent".equals(user.getRole()), user.getCreatedAt(),
                followRepository.countByFolloweeId(userId), followRepository.countByFollowerId(userId),
                followedByMe, statsService.forUser(userId));
    }

    @Transactional
    public void follow(UUID followerId, UUID followeeId) {
        if (followerId.equals(followeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't follow yourself");
        }
        findUser(followeeId);
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            Follow follow = new Follow();
            follow.setFollowerId(followerId);
            follow.setFolloweeId(followeeId);
            follow.setCreatedAt(Instant.now());
            followRepository.save(follow);
        }
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followeeId) {
        followRepository.deleteById(new Follow.FollowId(followerId, followeeId));
    }

    public PagedResponse<UserDto.Summary> followers(UUID userId, int page, int limit) {
        var result = followRepository.findByFolloweeIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page - 1, 0), limit));
        var users = lookups.users(result.getContent().stream().map(Follow::getFollowerId).toList());
        return PagedResponse.of(result, page, limit, f -> users.get(f.getFollowerId()));
    }

    public PagedResponse<UserDto.Summary> following(UUID userId, int page, int limit) {
        var result = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page - 1, 0), limit));
        var users = lookups.users(result.getContent().stream().map(Follow::getFolloweeId).toList());
        return PagedResponse.of(result, page, limit, f -> users.get(f.getFolloweeId()));
    }

    /**
     * Picks from people the viewer follows, newest lock first. Only locked fights appear,
     * so the feed can't be used to copy open picks.
     */
    public PagedResponse<FeedItem> feed(UUID viewerId, int page, int limit) {
        var params = new MapSqlParameterSource("viewerId", viewerId)
                .addValue("limit", limit)
                .addValue("offset", (long) Math.max(page - 1, 0) * limit);
        String from = """
                FROM picks p
                JOIN follows fo ON fo.followee_id = p.user_id AND fo.follower_id = :viewerId
                JOIN fights f ON f.id = p.fight_id
                JOIN events e ON e.id = f.event_id
                WHERE %s
                """.formatted(PickLock.SQL_LOCKED);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, params, Long.class);
        List<UUID> pickIds = jdbc.queryForList("SELECT p.id " + from
                        + " ORDER BY COALESCE(f.locked_at, f.starts_at, e.starts_at) DESC, p.created_at DESC"
                        + " LIMIT :limit OFFSET :offset",
                params, UUID.class);

        Map<UUID, Pick> picks = pickRepository.findAllById(pickIds).stream()
                .collect(Collectors.toMap(Pick::getId, Function.identity()));
        List<Fight> fights = fightRepository.findWithEventByIdIn(
                picks.values().stream().map(Pick::getFightId).collect(Collectors.toSet()));
        Map<UUID, Fight> fightById = fights.stream().collect(Collectors.toMap(Fight::getId, Function.identity()));
        Map<UUID, FightDto> fightDtos = lookups.fightDtos(fights).stream()
                .collect(Collectors.toMap(FightDto::id, Function.identity()));
        var users = lookups.users(picks.values().stream().map(Pick::getUserId).toList());

        List<FeedItem> items = pickIds.stream().map(picks::get).map(p -> {
            var e = fightById.get(p.getFightId()).getEvent();
            return new FeedItem(users.get(p.getUserId()), PickDto.PickResponse.from(p), fightDtos.get(p.getFightId()),
                    new FightDto.EventSummary(e.getId(), e.getSlug(), e.getName(), e.getStartsAt(), e.getStatus()));
        }).toList();
        return PagedResponse.of(items, page, limit, total == null ? 0 : total);
    }

    public User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static String blankToNull(String s) {
        return s.isBlank() ? null : s.strip();
    }
}
