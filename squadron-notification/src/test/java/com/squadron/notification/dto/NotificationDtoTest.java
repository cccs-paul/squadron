package com.squadron.notification.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationDto dto = NotificationDto.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(userId)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("SENT")
                .eventType("AGENT_COMPLETED")
                .createdAt(now)
                .sentAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(userId, dto.getUserId());
        assertEquals("IN_APP", dto.getChannel());
        assertEquals("Test", dto.getSubject());
        assertEquals("Body", dto.getBody());
        assertEquals("SENT", dto.getStatus());
        assertEquals("AGENT_COMPLETED", dto.getEventType());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getSentAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        NotificationDto dto = new NotificationDto();
        assertNull(dto.getId());
        assertNull(dto.getChannel());
    }

    @Test
    void should_supportSetters() {
        NotificationDto dto = new NotificationDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setStatus("READ");
        dto.setReadAt(Instant.now());

        assertEquals(id, dto.getId());
        assertEquals("READ", dto.getStatus());
    }
}
