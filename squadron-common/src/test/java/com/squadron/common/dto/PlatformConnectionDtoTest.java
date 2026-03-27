package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlatformConnectionDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("region", "us-east-1");
        Instant now = Instant.now();

        PlatformConnectionDto dto = PlatformConnectionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("GITHUB", dto.getPlatformType());
        assertEquals("https://api.github.com", dto.getBaseUrl());
        assertEquals("oauth2", dto.getAuthType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(metadata, dto.getMetadata());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        PlatformConnectionDto dto = new PlatformConnectionDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getPlatformType());
        assertNull(dto.getBaseUrl());
        assertNull(dto.getAuthType());
        assertNull(dto.getStatus());
        assertNull(dto.getMetadata());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("version", "v3");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        PlatformConnectionDto dto = new PlatformConnectionDto(
                id, tenantId, "JIRA", "https://jira.example.com",
                "pat", "CONNECTED", metadata, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("JIRA", dto.getPlatformType());
        assertEquals("https://jira.example.com", dto.getBaseUrl());
        assertEquals("pat", dto.getAuthType());
        assertEquals("CONNECTED", dto.getStatus());
        assertEquals(metadata, dto.getMetadata());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        PlatformConnectionDto dto = new PlatformConnectionDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setPlatformType("GITLAB");
        dto.setBaseUrl("https://gitlab.com");
        dto.setStatus("DISCONNECTED");

        assertEquals(id, dto.getId());
        assertEquals("GITLAB", dto.getPlatformType());
        assertEquals("https://gitlab.com", dto.getBaseUrl());
        assertEquals("DISCONNECTED", dto.getStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        PlatformConnectionDto dto1 = PlatformConnectionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("GITHUB")
                .build();

        PlatformConnectionDto dto2 = PlatformConnectionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("GITHUB")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        PlatformConnectionDto dto1 = PlatformConnectionDto.builder()
                .platformType("GITHUB")
                .build();

        PlatformConnectionDto dto2 = PlatformConnectionDto.builder()
                .platformType("JIRA")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        PlatformConnectionDto dto = PlatformConnectionDto.builder()
                .platformType("BITBUCKET")
                .baseUrl("https://bitbucket.org")
                .status("ACTIVE")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("BITBUCKET"));
        assertTrue(str.contains("https://bitbucket.org"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        PlatformConnectionDto dto = PlatformConnectionDto.builder()
                .id(null)
                .tenantId(null)
                .platformType(null)
                .metadata(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getPlatformType());
        assertNull(dto.getMetadata());
    }

    @Test
    void should_handleEmptyMetadata_when_emptyMapProvided() {
        PlatformConnectionDto dto = PlatformConnectionDto.builder()
                .metadata(Map.of())
                .build();

        assertNotNull(dto.getMetadata());
        assertTrue(dto.getMetadata().isEmpty());
    }
}
