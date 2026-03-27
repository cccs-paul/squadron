package com.squadron.agent.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SquadronConfigTest {

    @Test
    void should_buildSquadronConfig_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SquadronConfig config = SquadronConfig.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("Test Config")
                .config("{\"coding\": {\"provider\": \"openai\"}}")
                .build();

        assertEquals(id, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals(teamId, config.getTeamId());
        assertEquals(userId, config.getUserId());
        assertEquals("Test Config", config.getName());
        assertEquals("{\"coding\": {\"provider\": \"openai\"}}", config.getConfig());
    }

    @Test
    void should_createSquadronConfig_when_usingNoArgsConstructor() {
        SquadronConfig config = new SquadronConfig();
        assertNull(config.getId());
        assertNull(config.getTenantId());
        assertNull(config.getTeamId());
        assertNull(config.getUserId());
        assertNull(config.getName());
        assertNull(config.getConfig());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        SquadronConfig config = SquadronConfig.builder()
                .tenantId(UUID.randomUUID())
                .name("Test")
                .config("{}")
                .build();

        config.onCreate();

        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
        assertEquals(config.getCreatedAt(), config.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        SquadronConfig config = SquadronConfig.builder()
                .tenantId(UUID.randomUUID())
                .name("Test")
                .config("{}")
                .build();

        config.onCreate();
        Instant createdAt = config.getCreatedAt();

        Thread.sleep(5);
        config.onUpdate();

        assertEquals(createdAt, config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        SquadronConfig config = new SquadronConfig();
        UUID tenantId = UUID.randomUUID();

        config.setTenantId(tenantId);
        config.setName("Updated Config");
        config.setConfig("{\"key\": \"value\"}");

        assertEquals(tenantId, config.getTenantId());
        assertEquals("Updated Config", config.getName());
        assertEquals("{\"key\": \"value\"}", config.getConfig());
    }

    @Test
    void should_allowNullTeamAndUser_when_tenantLevelConfig() {
        SquadronConfig config = SquadronConfig.builder()
                .tenantId(UUID.randomUUID())
                .name("Tenant Config")
                .config("{}")
                .build();

        assertNull(config.getTeamId());
        assertNull(config.getUserId());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SquadronConfig c1 = SquadronConfig.builder().id(id).tenantId(tenantId).name("cfg").config("{}").build();
        SquadronConfig c2 = SquadronConfig.builder().id(id).tenantId(tenantId).name("cfg").config("{}").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
