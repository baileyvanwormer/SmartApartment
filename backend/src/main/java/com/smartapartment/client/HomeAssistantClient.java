package com.smartapartment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartapartment.config.SmartApartmentProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class HomeAssistantClient {

    private static final Logger log = LoggerFactory.getLogger(HomeAssistantClient.class);

    private final WebClient webClient;
    private final SmartApartmentProperties properties;

    public HomeAssistantClient(WebClient homeAssistantWebClient, SmartApartmentProperties properties) {
        this.webClient = homeAssistantWebClient;
        this.properties = properties;
    }

    public void playSpotifyPlaylist(String playlistUri) {
        Map<String, Object> body = new HashMap<>();
        body.put("entity_id", properties.homeAssistant().sonosEntity());
        body.put("media_content_id", playlistUri);
        body.put("media_content_type", "playlist");

        callService("media_player", "play_media", body);
    }

    public void activateScene(String sceneEntityId) {
        Map<String, Object> body = Map.of("entity_id", sceneEntityId);
        callService("scene", "turn_on", body);
    }

    public void speakOnSonos(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("entity_id", properties.homeAssistant().sonosEntity());
        body.put("message", message);

        callService("tts", "speak", body);
    }

    public PersonPresence getPersonPresence() {
        String entityId = properties.homeAssistant().personEntity();
        try {
            JsonNode state = webClient.get()
                    .uri("/api/states/{entityId}", entityId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            if (state == null) {
                return PersonPresence.unknown(entityId);
            }

            String status = state.path("state").asText("unknown");
            Instant lastChanged = Instant.parse(state.path("last_changed").asText(Instant.EPOCH.toString()));
            return new PersonPresence(entityId, status, lastChanged);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Person entity not found in Home Assistant: {}", entityId);
            return PersonPresence.unknown(entityId);
        }
    }

    private void callService(String domain, String service, Map<String, Object> body) {
        webClient.post()
                .uri("/api/services/{domain}/{service}", domain, service)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(10));

        log.info("Called HA service {}.{} with {}", domain, service, body);
    }

    public record PersonPresence(String entityId, String state, Instant lastChanged) {
        static PersonPresence unknown(String entityId) {
            return new PersonPresence(entityId, "unknown", Instant.EPOCH);
        }

        boolean isHome() {
            return "home".equalsIgnoreCase(state);
        }
    }
}
