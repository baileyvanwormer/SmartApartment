package com.smartapartment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartapartment")
public record SmartApartmentProperties(
        HomeAssistant homeAssistant,
        Spotify spotify,
        WelcomeHome welcomeHome,
        Security security
) {
    public record HomeAssistant(
            String url,
            String token,
            String sonosEntity,
            String hueSceneDouble,
            String hueSceneTriple,
            String hueSceneLong,
            String personEntity,
            String ttsEntity
    ) {}

    public record Spotify(String playlists) {}

    public record WelcomeHome(int arrivalWindowMinutes, String message) {}

    public record Security(String webhookSecret) {}
}
