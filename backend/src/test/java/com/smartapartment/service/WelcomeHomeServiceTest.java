package com.smartapartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartapartment.client.HomeAssistantClient;
import com.smartapartment.client.HomeAssistantClient.PersonPresence;
import com.smartapartment.config.SmartApartmentProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WelcomeHomeServiceTest {

    private static final String PERSON_ENTITY = "person.bailey";
    private static final String MESSAGE = "Hello Bailey, welcome home";
    private static final int ARRIVAL_WINDOW_MINUTES = 15;

    @Mock
    private HomeAssistantClient homeAssistantClient;

    private WelcomeHomeService welcomeHomeService;

    @BeforeEach
    void setUp() {
        SmartApartmentProperties properties = new SmartApartmentProperties(
                new SmartApartmentProperties.HomeAssistant(null, null, null, null, null, PERSON_ENTITY),
                new SmartApartmentProperties.Spotify(null),
                new SmartApartmentProperties.WelcomeHome(ARRIVAL_WINDOW_MINUTES, MESSAGE),
                new SmartApartmentProperties.Security(null));
        welcomeHomeService = new WelcomeHomeService(homeAssistantClient, properties);
    }

    @Test
    void skipsGreetingWhenNotHome() {
        when(homeAssistantClient.getPersonPresence())
                .thenReturn(new PersonPresence(PERSON_ENTITY, "not_home", Instant.now()));

        boolean greeted = welcomeHomeService.tryWelcomeHome();

        assertThat(greeted).isFalse();
        verify(homeAssistantClient, never()).speakOnSonos(any());
    }

    @Test
    void skipsGreetingWhenArrivalOutsideWindow() {
        Instant staleArrival = Instant.now().minusSeconds((ARRIVAL_WINDOW_MINUTES + 5) * 60L);
        when(homeAssistantClient.getPersonPresence())
                .thenReturn(new PersonPresence(PERSON_ENTITY, "home", staleArrival));

        boolean greeted = welcomeHomeService.tryWelcomeHome();

        assertThat(greeted).isFalse();
        verify(homeAssistantClient, never()).speakOnSonos(any());
    }

    @Test
    void greetsOnceWhenHomeWithinWindow() {
        when(homeAssistantClient.getPersonPresence())
                .thenReturn(new PersonPresence(PERSON_ENTITY, "home", Instant.now()));

        boolean greeted = welcomeHomeService.tryWelcomeHome();

        assertThat(greeted).isTrue();
        verify(homeAssistantClient, times(1)).speakOnSonos(MESSAGE);
    }

    @Test
    void skipsSecondGreetingSinceArrival() {
        when(homeAssistantClient.getPersonPresence())
                .thenReturn(new PersonPresence(PERSON_ENTITY, "home", Instant.now()));

        boolean firstAttempt = welcomeHomeService.tryWelcomeHome();
        boolean secondAttempt = welcomeHomeService.tryWelcomeHome();

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        verify(homeAssistantClient, times(1)).speakOnSonos(any());
    }

    @Test
    void resetAllowsGreetingAgain() {
        when(homeAssistantClient.getPersonPresence())
                .thenReturn(new PersonPresence(PERSON_ENTITY, "home", Instant.now()));

        welcomeHomeService.tryWelcomeHome();
        welcomeHomeService.resetGreeting();
        boolean greetedAfterReset = welcomeHomeService.tryWelcomeHome();

        assertThat(greetedAfterReset).isTrue();
        verify(homeAssistantClient, times(2)).speakOnSonos(any());
    }
}
