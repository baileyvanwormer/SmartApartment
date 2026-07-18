package com.smartapartment.service;

import com.smartapartment.client.HomeAssistantClient;
import com.smartapartment.client.HomeAssistantClient.PersonPresence;
import com.smartapartment.config.SmartApartmentProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WelcomeHomeService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeHomeService.class);

    private final HomeAssistantClient homeAssistantClient;
    private final SmartApartmentProperties properties;
    private volatile boolean greetedSinceArrival;

    public WelcomeHomeService(HomeAssistantClient homeAssistantClient, SmartApartmentProperties properties) {
        this.homeAssistantClient = homeAssistantClient;
        this.properties = properties;
    }

    public boolean tryWelcomeHome() {
        PersonPresence presence = homeAssistantClient.getPersonPresence();

        if (!presence.isHome()) {
            log.debug("Skipping welcome home: {} is {}", presence.entityId(), presence.state());
            return false;
        }

        if (greetedSinceArrival) {
            log.debug("Skipping welcome home: already greeted since arrival");
            return false;
        }

        Duration window = Duration.ofMinutes(properties.welcomeHome().arrivalWindowMinutes());
        if (Duration.between(presence.lastChanged(), Instant.now()).compareTo(window) > 0) {
            log.debug("Skipping welcome home: arrival outside {} minute window", window.toMinutes());
            return false;
        }

        homeAssistantClient.speakOnSonos(properties.welcomeHome().message());
        greetedSinceArrival = true;
        log.info("Welcome home greeting spoken on Sonos");
        return true;
    }

    public void resetGreeting() {
        greetedSinceArrival = false;
        log.info("Welcome home greeting reset");
    }
}
