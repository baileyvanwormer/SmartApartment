package com.smartapartment.controller;

import com.smartapartment.config.SmartApartmentProperties;
import com.smartapartment.model.ButtonActionResponse;
import com.smartapartment.model.ButtonPressType;
import com.smartapartment.service.ActionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionService actionService;
    private final SmartApartmentProperties properties;

    public ActionController(ActionService actionService, SmartApartmentProperties properties) {
        this.actionService = actionService;
        this.properties = properties;
    }

    @PostMapping("/button/{pressType}")
    public ButtonActionResponse handleButtonPress(
            @PathVariable String pressType,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        verifySecret(secret);
        ButtonPressType type = parsePressType(pressType);
        return actionService.handleButtonPress(type);
    }

    @PostMapping("/presence/away")
    public ButtonActionResponse handleAway(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        verifySecret(secret);
        return actionService.resetWelcomeHome();
    }

    private void verifySecret(String secret) {
        String expected = properties.security().webhookSecret();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
        }
    }

    private ButtonPressType parsePressType(String raw) {
        try {
            return ButtonPressType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid press type. Use single, double, triple, long, or skip");
        }
    }
}
