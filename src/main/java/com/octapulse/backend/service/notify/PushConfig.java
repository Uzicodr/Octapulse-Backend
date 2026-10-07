package com.octapulse.backend.service.notify;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.octapulse.backend.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sends pushes through Firebase Cloud Messaging when FIREBASE_CREDENTIALS holds a service-account
 * key (the JSON itself, or base64 of it). Without one, pushes are only logged; the in-app inbox
 * works either way.
 */
@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public PushSender pushSender(@Value("${push.fcm-credentials:}") String credentials,
                                 DeviceTokenRepository deviceTokenRepository) throws IOException {
        if (credentials.isBlank()) {
            log.info("FIREBASE_CREDENTIALS not set; push notifications are logged, not sent");
            return (devices, title, body, data) -> {
                if (!devices.isEmpty()) {
                    log.debug("push not configured; dropped '{}' for {} device(s)", title, devices.size());
                }
            };
        }
        String json = credentials.trim().startsWith("{")
                ? credentials.trim()
                : new String(Base64.getDecoder().decode(credentials.trim()), StandardCharsets.UTF_8);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                .build();
        FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
        log.info("push notifications go through Firebase project {}", app.getOptions().getProjectId());
        return new FcmPushSender(FirebaseMessaging.getInstance(app), deviceTokenRepository);
    }
}
