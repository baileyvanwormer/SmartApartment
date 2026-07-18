package com.smartapartment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActionServiceTest {

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
}
