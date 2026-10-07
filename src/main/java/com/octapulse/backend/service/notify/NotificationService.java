package com.octapulse.backend.service.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octapulse.backend.repository.DeviceTokenRepository;
import com.octapulse.backend.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    public static final String EVENT_REMINDER = "event_reminder";
    public static final String EVENT_SETTLED = "event_settled";
    public static final String FIGHT_BOOKED = "fight_booked";
    public static final String FIGHT_RESULT = "fight_result";
    public static final String EVENT_LIVE = "event_live";
    public static final String FIGHTER_NEWS = "fighter_news";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final NotificationSettingsService settings;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            DeviceTokenRepository deviceTokenRepository,
            PushSender pushSender,
            NotificationSettingsService settings,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores the notification and pushes it, unless this user already got one with the same
     * type and key. Safe to call repeatedly from jobs. The push is skipped, but the inbox entry kept,
     * when the user turned that kind off.
     */
    @Transactional
    public boolean notify(UUID userId, String type, String key, String title, String body, Map<String, Object> data) {
        return notify(userId, type, key, title, body, data, true);
    }

    /** As above; push=false stores the inbox entry only (used to cap how many pushes a user gets). */
    @Transactional
    public boolean notify(UUID userId, String type, String key, String title, String body, Map<String, Object> data,
                          boolean push) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        int inserted = notificationRepository.insertIfAbsent(userId, type, title, body, json, type + ":" + key);
        if (inserted == 0) {
            return false;
        }
        if (!push || !settings.get(userId).allows(type)) {
            return true;
        }
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        try {
            pushSender.send(deviceTokenRepository.findByUserId(userId), title, body, payload);
        } catch (RuntimeException e) {
            // The in-app notification is already stored; a failed push must not roll it back.
            log.warn("push delivery failed for user {}: {}", userId, e.getMessage());
        }
        return true;
    }
}
