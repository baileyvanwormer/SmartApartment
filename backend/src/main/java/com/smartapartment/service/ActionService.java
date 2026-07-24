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
    private final PlaylistService playlistService;
    private final SmartApartmentProperties properties;

    public ActionService(
            HomeAssistantClient homeAssistantClient,
            WelcomeHomeService welcomeHomeService,
            PlaylistService playlistService,
            SmartApartmentProperties properties) {
        this.homeAssistantClient = homeAssistantClient;
        this.welcomeHomeService = welcomeHomeService;
        this.playlistService = playlistService;
        this.properties = properties;
    }

    public ButtonActionResponse handleButtonPress(ButtonPressType pressType) {
        return switch (pressType) {
            case SINGLE -> playSpotify();
            case SKIP -> skipTrack();
            case DOUBLE -> activateHueScene(properties.homeAssistant().hueSceneDouble(), "relax");
            case TRIPLE -> activateHueScene(properties.homeAssistant().hueSceneTriple(), "party");
            case LONG -> nextPlaylist();
        };
    }

    public ButtonActionResponse resetWelcomeHome() {
        welcomeHomeService.resetGreeting();
        return new ButtonActionResponse("welcome_home_reset", "Welcome home state reset");
    }

    private ButtonActionResponse playSpotify() {
        String state = homeAssistantClient.getSonosState();

        if ("playing".equalsIgnoreCase(state)) {
            homeAssistantClient.pauseSonos();
            return new ButtonActionResponse("spotify_pause", "Paused Sonos");
        }

        if ("paused".equalsIgnoreCase(state)) {
            homeAssistantClient.resumeSonos();
            return new ButtonActionResponse("spotify_resume", "Resumed Sonos");
        }

        PlaylistService.PlaylistEntry playlist = playlistService.current();
        homeAssistantClient.playSpotifyPlaylist(toSpotifyPlaylistUri(playlist.url()));
        return new ButtonActionResponse("spotify_play", "Playing " + playlist.name() + " on Sonos");
    }

    private ButtonActionResponse skipTrack() {
        homeAssistantClient.skipToNextTrack();
        return new ButtonActionResponse("spotify_skip", "Skipped to next track");
    }

    private ButtonActionResponse activateHueScene(String sceneEntityId, String label) {
        homeAssistantClient.activateScene(sceneEntityId);
        return new ButtonActionResponse("hue_scene_" + label, "Activated scene " + sceneEntityId);
    }

    private ButtonActionResponse nextPlaylist() {
        PlaylistService.PlaylistEntry playlist = playlistService.advanceToNext();
        homeAssistantClient.speakOnSonos(playlist.name());
        homeAssistantClient.playSpotifyPlaylist(toSpotifyPlaylistUri(playlist.url()));
        return new ButtonActionResponse("playlist_switch", "Now playing: " + playlist.name());
    }

    static String toSpotifyPlaylistUri(String playlistUrl) {
        if (playlistUrl == null || playlistUrl.isBlank()) {
            throw new IllegalStateException("Playlist URL is not configured");
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
