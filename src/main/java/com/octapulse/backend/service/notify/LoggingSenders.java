package com.octapulse.backend.service.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Placeholder senders used until a real push/email provider is configured.
 * They never log message bodies, since those can carry password reset links.
 */
@Configuration
public class LoggingSenders {

    private static final Logger log = LoggerFactory.getLogger(LoggingSenders.class);

    @Bean
    @ConditionalOnMissingBean
    public PushSender pushSender() {
        return (devices, title, body, data) -> {
            if (!devices.isEmpty()) {
                log.debug("push not configured; dropped '{}' for {} device(s)", title, devices.size());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailSender emailSender() {
        return (to, subject, body) -> log.warn("email not configured; dropped '{}'", subject);
    }
}
