package com.octapulse.backend.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapulse.backend.domain.DeviceToken;
import com.octapulse.backend.domain.FighterFollow;
import com.octapulse.backend.domain.Notification;
import com.octapulse.backend.dto.FighterDto;
import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.dto.SocialDto.DeviceRequest;
import com.octapulse.backend.dto.SocialDto.DeviceUnregisterRequest;
import com.octapulse.backend.dto.SocialDto.FeedItem;
import com.octapulse.backend.dto.SocialDto.LeagueResponse;
import com.octapulse.backend.dto.SocialDto.NotificationResponse;
import com.octapulse.backend.dto.SocialDto.UnreadCount;
import com.octapulse.backend.dto.StatsDto;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.DeviceTokenRepository;
import com.octapulse.backend.repository.FighterFollowRepository;
import com.octapulse.backend.repository.NotificationRepository;
import com.octapulse.backend.service.LeagueService;
import com.octapulse.backend.service.Lookups;
import com.octapulse.backend.service.SocialService;
import com.octapulse.backend.service.StatsService;
import com.octapulse.backend.service.notify.NotificationSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The signed-in user's own data. All routes require auth (see WebConfig). */
@RestController
@RequestMapping("/me")
public class MeController {

    private final SocialService socialService;
    private final StatsService statsService;
    private final LeagueService leagueService;
    private final FighterFollowRepository fighterFollowRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationSettingsService notificationSettings;
    private final Lookups lookups;
    private final ObjectMapper objectMapper;

    public MeController(
            SocialService socialService,
            StatsService statsService,
            LeagueService leagueService,
            FighterFollowRepository fighterFollowRepository,
            NotificationRepository notificationRepository,
            DeviceTokenRepository deviceTokenRepository,
            NotificationSettingsService notificationSettings,
            Lookups lookups,
            ObjectMapper objectMapper
    ) {
        this.socialService = socialService;
        this.statsService = statsService;
        this.leagueService = leagueService;
        this.fighterFollowRepository = fighterFollowRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.notificationSettings = notificationSettings;
        this.lookups = lookups;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public UserDto.Me me(HttpServletRequest request) {
        return UserDto.Me.from(socialService.findUser(CurrentUser.require(request)));
    }

    @PatchMapping
    public UserDto.Me update(@Valid @RequestBody UserDto.UpdateMeRequest req, HttpServletRequest request) {
        return UserDto.Me.from(socialService.updateMe(CurrentUser.require(request), req));
    }

    @GetMapping("/stats")
    public StatsDto stats(HttpServletRequest request) {
        return statsService.forUser(CurrentUser.require(request));
    }

    @GetMapping("/feed")
    public PagedResponse<FeedItem> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request
    ) {
        return socialService.feed(CurrentUser.require(request), page, CurrentUser.clampLimit(limit));
    }

    @GetMapping("/fighter-follows")
    public List<FighterDto> fighterFollows(HttpServletRequest request) {
        var follows = fighterFollowRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.require(request));
        var fighters = lookups.fighters(follows.stream().map(FighterFollow::getFighterId).toList());
        return follows.stream()
                .map(f -> fighters.get(f.getFighterId()))
                .filter(Objects::nonNull)
                .map(FighterDto::from)
                .toList();
    }

    @GetMapping("/leagues")
    public List<LeagueResponse> leagues(HttpServletRequest request) {
        return leagueService.forUser(CurrentUser.require(request));
    }

    @GetMapping("/notifications")
    public PagedResponse<NotificationResponse> notifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request
    ) {
        UUID userId = CurrentUser.require(request);
        limit = CurrentUser.clampLimit(limit);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        var result = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PagedResponse.of(result, page, limit, this::toResponse);
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCount unreadCount(HttpServletRequest request) {
        return new UnreadCount(notificationRepository.countByUserIdAndReadAtIsNull(CurrentUser.require(request)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId, HttpServletRequest request) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, CurrentUser.require(request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            n = notificationRepository.save(n);
        }
        return toResponse(n);
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public UnreadCount markAllRead(HttpServletRequest request) {
        notificationRepository.markAllRead(CurrentUser.require(request), Instant.now());
        return new UnreadCount(0);
    }

    /** Which kinds of push this user gets. Everything is on until changed. */
    @GetMapping("/notification-settings")
    public NotificationSettingsService.Settings notificationSettings(HttpServletRequest request) {
        return notificationSettings.get(CurrentUser.require(request));
    }

    /** Turn kinds of push on or off; omitted fields keep their value. Returns the full settings. */
    @PatchMapping("/notification-settings")
    public NotificationSettingsService.Settings updateNotificationSettings(
            @RequestBody NotificationSettingsService.Update update, HttpServletRequest request) {
        return notificationSettings.update(CurrentUser.require(request), update);
    }

    /** Registers (or re-assigns) a push token for this device. */
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@Valid @RequestBody DeviceRequest req, HttpServletRequest request) {
        DeviceToken device = deviceTokenRepository.findById(req.token()).orElseGet(DeviceToken::new);
        device.setToken(req.token());
        device.setUserId(CurrentUser.require(request));
        device.setPlatform(req.platform());
        if (device.getCreatedAt() == null) {
            device.setCreatedAt(Instant.now());
        }
        deviceTokenRepository.save(device);
    }

    @PostMapping("/devices/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterDevice(@Valid @RequestBody DeviceUnregisterRequest req, HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        deviceTokenRepository.findById(req.token())
                .filter(d -> d.getUserId().equals(userId))
                .ifPresent(deviceTokenRepository::delete);
    }

    private NotificationResponse toResponse(Notification n) {
        JsonNode data = null;
        if (n.getData() != null) {
            try {
                data = objectMapper.readTree(n.getData());
            } catch (JsonProcessingException e) {
                data = null;
            }
        }
        return NotificationResponse.from(n, data);
    }
}
