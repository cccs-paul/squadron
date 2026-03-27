package com.squadron.notification.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePreferenceRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UpdatePreferenceRequest request = UpdatePreferenceRequest.builder()
                .enableEmail(true)
                .enableSlack(true)
                .enableTeams(false)
                .enableInApp(true)
                .slackWebhookUrl("https://hooks.slack.com/test")
                .emailAddress("user@example.com")
                .mutedEventTypes(List.of("AGENT_COMPLETED", "REVIEW_UPDATED"))
                .build();

        assertTrue(request.getEnableEmail());
        assertTrue(request.getEnableSlack());
        assertFalse(request.getEnableTeams());
        assertTrue(request.getEnableInApp());
        assertEquals("https://hooks.slack.com/test", request.getSlackWebhookUrl());
        assertEquals("user@example.com", request.getEmailAddress());
        assertEquals(2, request.getMutedEventTypes().size());
    }

    @Test
    void should_useNoArgsConstructor() {
        UpdatePreferenceRequest request = new UpdatePreferenceRequest();
        assertNull(request.getEnableEmail());
        assertNull(request.getSlackWebhookUrl());
    }

    @Test
    void should_supportSetters() {
        UpdatePreferenceRequest request = new UpdatePreferenceRequest();
        request.setEnableSlack(true);
        request.setTeamsWebhookUrl("https://outlook.webhook.office.com/test");

        assertTrue(request.getEnableSlack());
        assertEquals("https://outlook.webhook.office.com/test", request.getTeamsWebhookUrl());
    }
}
