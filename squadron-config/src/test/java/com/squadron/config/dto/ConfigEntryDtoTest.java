package com.squadron.config.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigEntryDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntryDto dto = ConfigEntryDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .configKey("max.retries")
                .configValue("5")
                .description("Maximum retries")
                .createdAt(now)
                .updatedAt(now)
                .createdBy(createdBy)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
        assertEquals("max.retries", dto.getConfigKey());
        assertEquals("5", dto.getConfigValue());
        assertEquals("Maximum retries", dto.getDescription());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
        assertEquals(createdBy, dto.getCreatedBy());
    }

    @Test
    void should_useNoArgsConstructor() {
        ConfigEntryDto dto = new ConfigEntryDto();
        assertNull(dto.getId());
        assertNull(dto.getConfigKey());
        assertNull(dto.getConfigValue());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntryDto dto = new ConfigEntryDto(id, tenantId, teamId, userId,
                "key", "value", "desc", now, now, createdBy);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
        assertEquals("key", dto.getConfigKey());
        assertEquals("value", dto.getConfigValue());
        assertEquals("desc", dto.getDescription());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
        assertEquals(createdBy, dto.getCreatedBy());
    }

    @Test
    void should_supportSetters() {
        ConfigEntryDto dto = new ConfigEntryDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setConfigKey("my.key");
        dto.setConfigValue("my.value");
        dto.setDescription("A description");

        assertEquals(id, dto.getId());
        assertEquals("my.key", dto.getConfigKey());
        assertEquals("my.value", dto.getConfigValue());
        assertEquals("A description", dto.getDescription());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntryDto d1 = ConfigEntryDto.builder()
                .id(id).configKey("key").configValue("val").createdAt(now).updatedAt(now).build();
        ConfigEntryDto d2 = ConfigEntryDto.builder()
                .id(id).configKey("key").configValue("val").createdAt(now).updatedAt(now).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        ConfigEntryDto d1 = ConfigEntryDto.builder().configKey("key1").build();
        ConfigEntryDto d2 = ConfigEntryDto.builder().configKey("key2").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_supportToString() {
        ConfigEntryDto dto = ConfigEntryDto.builder().configKey("test").build();
        assertNotNull(dto.toString());
        assert dto.toString().contains("test");
    }
}
