package com.squadron.notification.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationPreferenceDto dto = NotificationPreferenceDto.builder()
                .id(id)
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress("user@example.com")
                .mutedEventTypes(List.of("TASK_STATE_CHANGED"))
                .build();

        assertEquals(id, dto.getId());
        assertEquals(userId, dto.getUserId());
        assertEquals(tenantId, dto.getTenantId());
        assertTrue(dto.getEnableEmail());
        assertEquals("user@example.com", dto.getEmailAddress());
        assertEquals(1, dto.getMutedEventTypes().size());
        assertEquals("TASK_STATE_CHANGED", dto.getMutedEventTypes().get(0));
    }

    @Test
    void should_useNoArgsConstructor() {
        NotificationPreferenceDto dto = new NotificationPreferenceDto();
        assertNull(dto.getId());
        assertNull(dto.getUserId());
    }

    @Test
    void should_supportSetters() {
        NotificationPreferenceDto dto = new NotificationPreferenceDto();
        dto.setSlackWebhookUrl("https://hooks.slack.com/test");
        dto.setTeamsWebhookUrl("https://outlook.webhook.office.com/test");

        assertEquals("https://hooks.slack.com/test", dto.getSlackWebhookUrl());
        assertEquals("https://outlook.webhook.office.com/test", dto.getTeamsWebhookUrl());
    }
}
