package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SquadronConfigDtoTest {

    @Test
    void should_buildSquadronConfigDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> config = Map.of("coding", Map.of("provider", "openai"));

        SquadronConfigDto dto = SquadronConfigDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("Default Config")
                .config(config)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
        assertEquals("Default Config", dto.getName());
        assertEquals(config, dto.getConfig());
    }

    @Test
    void should_createSquadronConfigDto_when_usingNoArgsConstructor() {
        SquadronConfigDto dto = new SquadronConfigDto();
        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTeamId());
        assertNull(dto.getUserId());
        assertNull(dto.getName());
        assertNull(dto.getConfig());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        SquadronConfigDto dto = new SquadronConfigDto();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> config = Map.of("key", "value");

        dto.setTenantId(tenantId);
        dto.setName("Test Config");
        dto.setConfig(config);

        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Test Config", dto.getName());
        assertEquals(config, dto.getConfig());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID tenantId = UUID.randomUUID();
        SquadronConfigDto d1 = SquadronConfigDto.builder().tenantId(tenantId).name("test").build();
        SquadronConfigDto d2 = SquadronConfigDto.builder().tenantId(tenantId).name("test").build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        SquadronConfigDto d1 = SquadronConfigDto.builder().name("config1").build();
        SquadronConfigDto d2 = SquadronConfigDto.builder().name("config2").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_handleNullConfig_when_noConfigSet() {
        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(UUID.randomUUID())
                .name("No Config")
                .build();

        assertNull(dto.getConfig());
    }
}
