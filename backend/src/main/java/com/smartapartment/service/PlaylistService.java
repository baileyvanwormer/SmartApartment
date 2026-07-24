package com.smartapartment.service;

import com.smartapartment.config.SmartApartmentProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaylistService {

    private final SmartApartmentProperties properties;
    private volatile int currentIndex = 0;

    public PlaylistService(SmartApartmentProperties properties) {
        this.properties = properties;
    }

    public PlaylistEntry current() {
        List<PlaylistEntry> playlists = parsePlaylists();
        return playlists.get(currentIndex % playlists.size());
    }

    public PlaylistEntry advanceToNext() {
        List<PlaylistEntry> playlists = parsePlaylists();
        currentIndex = (currentIndex + 1) % playlists.size();
        return playlists.get(currentIndex);
    }

    private List<PlaylistEntry> parsePlaylists() {
        String raw = properties.spotify().playlists();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("SPOTIFY_PLAYLISTS is not configured");
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(PlaylistService::parseEntry)
                .toList();
    }

    private static PlaylistEntry parseEntry(String entry) {
        int separatorIndex = entry.indexOf('|');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException(
                    "Invalid playlist entry (expected \"Name|url\"): " + entry);
        }
        String name = entry.substring(0, separatorIndex).trim();
        String url = entry.substring(separatorIndex + 1).trim();
        return new PlaylistEntry(name, url);
    }

    public record PlaylistEntry(String name, String url) {}
}
