package com.smartapartment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmartApartmentProperties.class)
public class AppConfig {

    @Bean
    RestClient homeAssistantRestClient(RestClient.Builder builder, SmartApartmentProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return builder
                .baseUrl(properties.homeAssistant().url())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.homeAssistant().token())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
