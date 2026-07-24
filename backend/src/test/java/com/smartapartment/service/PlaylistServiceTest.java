package com.smartapartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartapartment.config.SmartApartmentProperties;
import org.junit.jupiter.api.Test;

class PlaylistServiceTest {

    private PlaylistService serviceWith(String playlists) {
        SmartApartmentProperties properties = new SmartApartmentProperties(
                new SmartApartmentProperties.HomeAssistant(null, null, null, null, null, null, null, null),
                new SmartApartmentProperties.Spotify(playlists),
                new SmartApartmentProperties.WelcomeHome(15, null),
                new SmartApartmentProperties.Security(null));
        return new PlaylistService(properties);
    }

    @Test
    void startsOnFirstPlaylist() {
        PlaylistService service = serviceWith("Chill|https://example.com/chill,Party|https://example.com/party");

        assertThat(service.current().name()).isEqualTo("Chill");
        assertThat(service.current().url()).isEqualTo("https://example.com/chill");
    }

    @Test
    void advancesToNextPlaylist() {
        PlaylistService service = serviceWith("Chill|https://example.com/chill,Party|https://example.com/party");

        PlaylistService.PlaylistEntry next = service.advanceToNext();

        assertThat(next.name()).isEqualTo("Party");
        assertThat(service.current().name()).isEqualTo("Party");
    }

    @Test
    void wrapsAroundToFirstPlaylistAfterLast() {
        PlaylistService service = serviceWith("Chill|https://example.com/chill,Party|https://example.com/party");

        service.advanceToNext();
        PlaylistService.PlaylistEntry wrapped = service.advanceToNext();

        assertThat(wrapped.name()).isEqualTo("Chill");
    }

    @Test
    void throwsWhenNotConfigured() {
        PlaylistService service = serviceWith("");

        assertThrows(IllegalStateException.class, service::current);
    }

    @Test
    void throwsOnMalformedEntry() {
        PlaylistService service = serviceWith("NoSeparatorHere");

        assertThrows(IllegalArgumentException.class, service::current);
    }
}
