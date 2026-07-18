package com.smartapartment.service;

import com.smartapartment.client.HomeAssistantClient;
import com.smartapartment.config.SmartApartmentProperties;
import com.smartapartment.model.ButtonActionResponse;
import com.smartapartment.model.ButtonPressType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ActionService {

    private static final Pattern PLAYLIST_ID_PATTERN =
            Pattern.compile("(?:playlist/|spotify:playlist:)([a-zA-Z0-9]+)");

    private final HomeAssistantClient homeAssistantClient;
    private final WelcomeHomeService welcomeHomeService;
    private final SmartApartmentProperties properties;

    public ActionService(
            HomeAssistantClient homeAssistantClient,
            WelcomeHomeService welcomeHomeService,
            SmartApartmentProperties properties) {
        this.homeAssistantClient = homeAssistantClient;
        this.welcomeHomeService = welcomeHomeService;
        this.properties = properties;
    }

    public ButtonActionResponse handleButtonPress(ButtonPressType pressType) {
        return switch (pressType) {
            case SINGLE -> playSpotify();
            case DOUBLE -> activateHueScene(properties.homeAssistant().hueSceneDouble(), "relax");
            case TRIPLE -> activateHueScene(properties.homeAssistant().hueSceneTriple(), "party");
            case LONG -> welcomeHome();
        };
    }

    public ButtonActionResponse resetWelcomeHome() {
        welcomeHomeService.resetGreeting();
        return new ButtonActionResponse("welcome_home_reset", "Welcome home state reset");
    }

    private ButtonActionResponse playSpotify() {
        String playlistUri = toSpotifyPlaylistUri(properties.spotify().playlistUrl());
        homeAssistantClient.playSpotifyPlaylist(playlistUri);
        return new ButtonActionResponse("spotify_play", "Playing playlist on Sonos");
    }

    private ButtonActionResponse activateHueScene(String sceneEntityId, String label) {
        homeAssistantClient.activateScene(sceneEntityId);
        return new ButtonActionResponse("hue_scene_" + label, "Activated scene " + sceneEntityId);
    }

    private ButtonActionResponse welcomeHome() {
        boolean greeted = welcomeHomeService.tryWelcomeHome();
        if (greeted) {
            return new ButtonActionResponse("welcome_home", properties.welcomeHome().message());
        }
        return new ButtonActionResponse("welcome_home_skipped", "Welcome home conditions not met");
    }

    static String toSpotifyPlaylistUri(String playlistUrl) {
        if (playlistUrl == null || playlistUrl.isBlank()) {
            throw new IllegalStateException("SPOTIFY_PLAYLIST_URL is not configured");
        }

        if (playlistUrl.startsWith("spotify:playlist:")) {
            return playlistUrl;
        }

        Matcher matcher = PLAYLIST_ID_PATTERN.matcher(playlistUrl);
        if (matcher.find()) {
            return "spotify:playlist:" + matcher.group(1);
        }

        throw new IllegalArgumentException("Invalid Spotify playlist URL: " + playlistUrl);
    }
}
