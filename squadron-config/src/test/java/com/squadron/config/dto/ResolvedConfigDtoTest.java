package com.squadron.config.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResolvedConfigDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResolvedConfigDto dto = ResolvedConfigDto.builder()
                .configKey("max.retries")
                .resolvedValue("5")
                .resolvedFrom("USER")
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .build();

        assertEquals("max.retries", dto.getConfigKey());
        assertEquals("5", dto.getResolvedValue());
        assertEquals("USER", dto.getResolvedFrom());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
    }

    @Test
    void should_useNoArgsConstructor() {
        ResolvedConfigDto dto = new ResolvedConfigDto();
        assertNull(dto.getConfigKey());
        assertNull(dto.getResolvedValue());
        assertNull(dto.getResolvedFrom());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResolvedConfigDto dto = new ResolvedConfigDto("key", "value", "TEAM", tenantId, teamId, userId);

        assertEquals("key", dto.getConfigKey());
        assertEquals("value", dto.getResolvedValue());
        assertEquals("TEAM", dto.getResolvedFrom());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
    }

    @Test
    void should_supportSetters() {
        ResolvedConfigDto dto = new ResolvedConfigDto();
        dto.setConfigKey("my.key");
        dto.setResolvedValue("my.value");
        dto.setResolvedFrom("TENANT");

        assertEquals("my.key", dto.getConfigKey());
        assertEquals("my.value", dto.getResolvedValue());
        assertEquals("TENANT", dto.getResolvedFrom());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();

        ResolvedConfigDto d1 = ResolvedConfigDto.builder()
                .configKey("key").resolvedValue("val").resolvedFrom("TENANT").tenantId(tenantId).build();
        ResolvedConfigDto d2 = ResolvedConfigDto.builder()
                .configKey("key").resolvedValue("val").resolvedFrom("TENANT").tenantId(tenantId).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentResolvedFrom() {
        ResolvedConfigDto d1 = ResolvedConfigDto.builder()
                .configKey("key").resolvedValue("val").resolvedFrom("TENANT").build();
        ResolvedConfigDto d2 = ResolvedConfigDto.builder()
                .configKey("key").resolvedValue("val").resolvedFrom("USER").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_supportToString() {
        ResolvedConfigDto dto = ResolvedConfigDto.builder()
                .configKey("test.key").resolvedFrom("DEFAULT").build();
        assertNotNull(dto.toString());
        assert dto.toString().contains("test.key");
    }

    @Test
    void should_handleNullTeamAndUser_when_tenantLevel() {
        UUID tenantId = UUID.randomUUID();

        ResolvedConfigDto dto = ResolvedConfigDto.builder()
                .configKey("key")
                .resolvedValue("val")
                .resolvedFrom("TENANT")
                .tenantId(tenantId)
                .build();

        assertNull(dto.getTeamId());
        assertNull(dto.getUserId());
        assertEquals(tenantId, dto.getTenantId());
    }
}
