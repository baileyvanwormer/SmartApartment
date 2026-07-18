package com.smartapartment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(SmartApartmentProperties.class)
public class AppConfig {

    @Bean
    WebClient homeAssistantWebClient(SmartApartmentProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.homeAssistant().url())
                .defaultHeader("Authorization", "Bearer " + properties.homeAssistant().token())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
