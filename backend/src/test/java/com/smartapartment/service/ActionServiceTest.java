package com.smartapartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartapartment.client.HomeAssistantClient;
import com.smartapartment.config.SmartApartmentProperties;
import com.smartapartment.model.ButtonPressType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    private static final String PLAYLIST_URL = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
    private static final String HUE_SCENE_LONG = "scene.living_room_living_room_button_4";

    @Mock
    private HomeAssistantClient homeAssistantClient;

    @Mock
    private WelcomeHomeService welcomeHomeService;

    private ActionService actionService;

    @BeforeEach
    void setUp() {
        SmartApartmentProperties properties = new SmartApartmentProperties(
                new SmartApartmentProperties.HomeAssistant(
                        null, null, null, null, null, HUE_SCENE_LONG, null, null),
                new SmartApartmentProperties.Spotify(PLAYLIST_URL),
                new SmartApartmentProperties.WelcomeHome(15, null),
                new SmartApartmentProperties.Security(null));
        actionService = new ActionService(homeAssistantClient, welcomeHomeService, properties);
    }

    @Test
    void convertsSpotifyUrlToUri() {
        assertEquals(
                "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                ActionService.toSpotifyPlaylistUri(
                        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"));
    }

    @Test
    void acceptsSpotifyUriDirectly() {
        assertEquals(
                "spotify:playlist:abc123",
                ActionService.toSpotifyPlaylistUri("spotify:playlist:abc123"));
    }

    @Test
    void rejectsInvalidPlaylistUrl() {
        assertThrows(IllegalArgumentException.class, () -> ActionService.toSpotifyPlaylistUri("not-a-url"));
    }

    @Test
    void singlePressPlaysPlaylistWhenNothingIsPlaying() {
        when(homeAssistantClient.getSonosState()).thenReturn("idle");

        var response = actionService.handleButtonPress(ButtonPressType.SINGLE);

        assertThat(response.action()).isEqualTo("spotify_play");
        verify(homeAssistantClient).playSpotifyPlaylist("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M");
        verify(homeAssistantClient, never()).pauseSonos();
        verify(homeAssistantClient, never()).resumeSonos();
    }

    @Test
    void singlePressPausesWhenAlreadyPlaying() {
        when(homeAssistantClient.getSonosState()).thenReturn("playing");

        var response = actionService.handleButtonPress(ButtonPressType.SINGLE);

        assertThat(response.action()).isEqualTo("spotify_pause");
        verify(homeAssistantClient).pauseSonos();
        verify(homeAssistantClient, never()).playSpotifyPlaylist(any());
    }

    @Test
    void singlePressResumesWithoutRestartingWhenPaused() {
        when(homeAssistantClient.getSonosState()).thenReturn("paused");

        var response = actionService.handleButtonPress(ButtonPressType.SINGLE);

        assertThat(response.action()).isEqualTo("spotify_resume");
        verify(homeAssistantClient).resumeSonos();
        verify(homeAssistantClient, never()).playSpotifyPlaylist(any());
        verify(homeAssistantClient, never()).pauseSonos();
    }

    @Test
    void skipCallsNextTrack() {
        var response = actionService.handleButtonPress(ButtonPressType.SKIP);

        assertThat(response.action()).isEqualTo("spotify_skip");
        verify(homeAssistantClient).skipToNextTrack();
    }

    @Test
    void longPressActivatesSceneRegardlessOfGreeting() {
        when(welcomeHomeService.tryWelcomeHome()).thenReturn(false);

        actionService.handleButtonPress(ButtonPressType.LONG);

        verify(homeAssistantClient).activateScene(HUE_SCENE_LONG);
    }
}
