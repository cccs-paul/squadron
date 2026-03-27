package com.squadron.notification.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceTest {

    @Test
    void should_buildPreference_when_usingBuilder() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationPreference preference = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .slackWebhookUrl("https://hooks.slack.com/test")
                .teamsWebhookUrl("https://outlook.webhook.office.com/test")
                .emailAddress("user@example.com")
                .build();

        assertEquals(userId, preference.getUserId());
        assertEquals(tenantId, preference.getTenantId());
        assertTrue(preference.getEnableEmail());
        assertFalse(preference.getEnableSlack());
        assertFalse(preference.getEnableTeams());
        assertTrue(preference.getEnableInApp());
        assertEquals("https://hooks.slack.com/test", preference.getSlackWebhookUrl());
        assertEquals("https://outlook.webhook.office.com/test", preference.getTeamsWebhookUrl());
        assertEquals("user@example.com", preference.getEmailAddress());
    }

    @Test
    void should_setDefaultBooleans_when_usingBuilder() {
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .build();

        assertTrue(preference.getEnableEmail());
        assertFalse(preference.getEnableSlack());
        assertFalse(preference.getEnableTeams());
        assertTrue(preference.getEnableInApp());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .build();

        preference.onCreate();

        assertNotNull(preference.getCreatedAt());
        assertNotNull(preference.getUpdatedAt());
    }

    @Test
    void should_notOverrideTimestamps_when_alreadySet() {
        Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        preference.onCreate();

        assertEquals(fixedTime, preference.getCreatedAt());
        assertEquals(fixedTime, preference.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .build();

        preference.onCreate();
        Instant originalUpdated = preference.getUpdatedAt();

        Thread.sleep(10);
        preference.onUpdate();

        assertNotNull(preference.getUpdatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        NotificationPreference preference = new NotificationPreference();
        assertNull(preference.getId());
        assertNull(preference.getUserId());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationPreference preference = new NotificationPreference(
                id, userId, tenantId, true, true, false, true,
                "https://hooks.slack.com/test", null, "user@example.com",
                "[\"TASK_STATE_CHANGED\"]", now, now
        );

        assertEquals(id, preference.getId());
        assertEquals(userId, preference.getUserId());
        assertEquals(tenantId, preference.getTenantId());
        assertTrue(preference.getEnableEmail());
        assertTrue(preference.getEnableSlack());
        assertFalse(preference.getEnableTeams());
        assertTrue(preference.getEnableInApp());
        assertEquals("https://hooks.slack.com/test", preference.getSlackWebhookUrl());
        assertNull(preference.getTeamsWebhookUrl());
        assertEquals("user@example.com", preference.getEmailAddress());
        assertEquals("[\"TASK_STATE_CHANGED\"]", preference.getMutedEventTypes());
        assertEquals(now, preference.getCreatedAt());
        assertEquals(now, preference.getUpdatedAt());
    }

    @Test
    void should_supportSetters() {
        NotificationPreference preference = new NotificationPreference();
        UUID id = UUID.randomUUID();
        preference.setId(id);
        preference.setEnableSlack(true);
        preference.setMutedEventTypes("[\"AGENT_COMPLETED\"]");

        assertEquals(id, preference.getId());
        assertTrue(preference.getEnableSlack());
        assertEquals("[\"AGENT_COMPLETED\"]", preference.getMutedEventTypes());
    }
}
