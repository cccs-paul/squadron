package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhookPayloadTest {

    @Test
    void should_buildWithAllFields() {
        Instant now = Instant.now();
        Map<String, Object> payload = Map.of("action", "created", "issue", Map.of("id", 1));

        WebhookPayload webhookPayload = WebhookPayload.builder()
                .platform("GITHUB")
                .eventType("issues")
                .payload(payload)
                .receivedAt(now)
                .build();

        assertEquals("GITHUB", webhookPayload.getPlatform());
        assertEquals("issues", webhookPayload.getEventType());
        assertEquals(payload, webhookPayload.getPayload());
        assertEquals(now, webhookPayload.getReceivedAt());
    }

    @Test
    void should_haveDefaultReceivedAt() {
        WebhookPayload payload = WebhookPayload.builder()
                .platform("JIRA")
                .build();
        assertNotNull(payload.getReceivedAt());
    }

    @Test
    void should_supportNoArgsConstructor() {
        WebhookPayload payload = new WebhookPayload();
        assertNull(payload.getPlatform());
        assertNotNull(payload.getReceivedAt());
    }

    @Test
    void should_supportSetters() {
        WebhookPayload payload = new WebhookPayload();
        payload.setPlatform("GITLAB");
        payload.setEventType("push");
        assertEquals("GITLAB", payload.getPlatform());
        assertEquals("push", payload.getEventType());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        Instant now = Instant.now();
        WebhookPayload p1 = WebhookPayload.builder()
                .platform("JIRA")
                .eventType("issue_created")
                .receivedAt(now)
                .build();
        WebhookPayload p2 = WebhookPayload.builder()
                .platform("JIRA")
                .eventType("issue_created")
                .receivedAt(now)
                .build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_implementToString() {
        WebhookPayload payload = WebhookPayload.builder()
                .platform("GITHUB")
                .eventType("push")
                .build();
        assertNotNull(payload.toString());
        assertTrue(payload.toString().contains("GITHUB"));
    }
}
