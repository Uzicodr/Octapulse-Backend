package com.octapulse.backend.service.notify;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.octapulse.backend.domain.DeviceToken;
import com.octapulse.backend.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sends one notification to every device of a user and forgets tokens Firebase says are dead. */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final int MAX_TOKENS_PER_CALL = 500;
    private static final Set<MessagingErrorCode> DEAD_TOKEN = Set.of(
            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging messaging;
    private final DeviceTokenRepository deviceTokenRepository;

    public FcmPushSender(FirebaseMessaging messaging, DeviceTokenRepository deviceTokenRepository) {
        this.messaging = messaging;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    public void send(List<DeviceToken> devices, String title, String body, Map<String, Object> data) {
        // FCM data values must be strings.
        Map<String, String> payload = new LinkedHashMap<>();
        data.forEach((k, v) -> {
            if (v != null) {
                payload.put(k, v.toString());
            }
        });
        for (int start = 0; start < devices.size(); start += MAX_TOKENS_PER_CALL) {
            List<DeviceToken> batch = devices.subList(start, Math.min(devices.size(), start + MAX_TOKENS_PER_CALL));
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(batch.stream().map(DeviceToken::getToken).toList())
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(payload)
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                    .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setSound("default").build()).build())
                    .build();
            try {
                forgetDeadTokens(batch, messaging.sendEachForMulticast(message));
            } catch (FirebaseMessagingException e) {
                throw new IllegalStateException("FCM send failed: " + e.getMessage(), e);
            }
        }
    }

    private void forgetDeadTokens(List<DeviceToken> batch, BatchResponse response) {
        List<String> dead = new ArrayList<>();
        List<SendResponse> results = response.getResponses();
        for (int i = 0; i < results.size(); i++) {
            FirebaseMessagingException error = results.get(i).getException();
            if (error != null && DEAD_TOKEN.contains(error.getMessagingErrorCode())) {
                dead.add(batch.get(i).getToken());
            }
        }
        if (!dead.isEmpty()) {
            deviceTokenRepository.deleteAllById(dead);
            log.info("removed {} expired push token(s)", dead.size());
        }
    }
}
