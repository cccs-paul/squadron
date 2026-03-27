package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> settings = Map.of("maxProjects", 10, "autoAssign", true);
        Instant now = Instant.now();

        TeamDto dto = TeamDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Backend Team")
                .settings(settings)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Backend Team", dto.getName());
        assertEquals(settings, dto.getSettings());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        TeamDto dto = new TeamDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> settings = Map.of("region", "eu-west-1");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        TeamDto dto = new TeamDto(id, tenantId, "Frontend Team", settings, created, updated);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Frontend Team", dto.getName());
        assertEquals(settings, dto.getSettings());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        TeamDto dto = new TeamDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setName("QA Team");
        dto.setSettings(Map.of("env", "staging"));

        assertEquals(id, dto.getId());
        assertEquals("QA Team", dto.getName());
        assertEquals(Map.of("env", "staging"), dto.getSettings());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TeamDto dto1 = TeamDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Team X")
                .build();

        TeamDto dto2 = TeamDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Team X")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TeamDto dto1 = TeamDto.builder()
                .name("Team A")
                .build();

        TeamDto dto2 = TeamDto.builder()
                .name("Team B")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        TeamDto dto = TeamDto.builder()
                .name("DevOps Team")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("DevOps Team"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        TeamDto dto = TeamDto.builder()
                .id(null)
                .tenantId(null)
                .name(null)
                .settings(null)
                .createdAt(null)
                .updatedAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_handleEmptySettings_when_emptyMapProvided() {
        TeamDto dto = TeamDto.builder()
                .settings(Map.of())
                .build();

        assertNotNull(dto.getSettings());
        assertTrue(dto.getSettings().isEmpty());
    }
}
