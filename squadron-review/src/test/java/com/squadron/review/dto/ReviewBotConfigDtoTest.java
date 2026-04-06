package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewBotConfigDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewBotConfigDto dto = ReviewBotConfigDto.builder()
                .id(id)
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .enabled(true)
                .autoAssign(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(connectionId, dto.getConnectionId());
        assertEquals("squadron-bot", dto.getBotUsername());
        assertTrue(dto.isEnabled());
        assertEquals(false, dto.isAutoAssign());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewBotConfigDto dto = new ReviewBotConfigDto();
        assertNull(dto.getId());
        assertNull(dto.getBotUsername());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ReviewBotConfigDto d1 = ReviewBotConfigDto.builder()
                .id(id).tenantId(tenantId).botUsername("bot").build();
        ReviewBotConfigDto d2 = ReviewBotConfigDto.builder()
                .id(id).tenantId(tenantId).botUsername("bot").build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_supportToString() {
        ReviewBotConfigDto dto = ReviewBotConfigDto.builder()
                .botUsername("bot")
                .build();
        assertNotNull(dto.toString());
    }
}
