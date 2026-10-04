package com.octapulse.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({CitoProperties.class, JwtProperties.class})
public class AppConfig {

    @Bean
    public WebClient citoWebClient(CitoProperties props) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .exchangeStrategies(strategies)
                .build();
    }
}
