package com.octapulse.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cito")
public record CitoProperties(String apiKey, int monthlyCap, String baseUrl) {
}
