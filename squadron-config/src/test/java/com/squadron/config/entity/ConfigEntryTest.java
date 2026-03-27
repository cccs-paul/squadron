package com.squadron.config.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigEntryTest {

    @Test
    void should_buildEntry_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .configKey("max.retries")
                .configValue("5")
                .description("Maximum retries")
                .build();

        assertEquals(tenantId, entry.getTenantId());
        assertEquals(teamId, entry.getTeamId());
        assertEquals(userId, entry.getUserId());
        assertEquals("max.retries", entry.getConfigKey());
        assertEquals("5", entry.getConfigValue());
        assertEquals("Maximum retries", entry.getDescription());
        assertEquals(1, entry.getVersion()); // default value
    }

    @Test
    void should_setDefaultVersion_when_notExplicitlySet() {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("key")
                .configValue("value")
                .build();

        assertEquals(1, entry.getVersion());
    }

    @Test
    void should_allowExplicitVersion() {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("key")
                .configValue("value")
                .version(5)
                .build();

        assertEquals(5, entry.getVersion());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("key")
                .configValue("value")
                .build();

        entry.onCreate();

        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getUpdatedAt());
        assertEquals(entry.getCreatedAt(), entry.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("key")
                .configValue("value")
                .build();

        entry.onCreate();
        Instant originalCreated = entry.getCreatedAt();
        Instant originalUpdated = entry.getUpdatedAt();

        Thread.sleep(10);
        entry.onUpdate();

        assertNotNull(entry.getUpdatedAt());
        assertEquals(originalCreated, entry.getCreatedAt()); // createdAt should not change
    }

    @Test
    void should_allowNullTeamAndUser_when_tenantLevelConfig() {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("key")
                .configValue("value")
                .build();

        assertNull(entry.getTeamId());
        assertNull(entry.getUserId());
    }

    @Test
    void should_useNoArgsConstructor() {
        ConfigEntry entry = new ConfigEntry();
        assertNull(entry.getId());
        assertNull(entry.getTenantId());
        assertNull(entry.getConfigKey());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntry entry = new ConfigEntry(id, tenantId, teamId, userId,
                "key", "value", 3, "desc", now, now, createdBy);

        assertEquals(id, entry.getId());
        assertEquals(tenantId, entry.getTenantId());
        assertEquals(teamId, entry.getTeamId());
        assertEquals(userId, entry.getUserId());
        assertEquals("key", entry.getConfigKey());
        assertEquals("value", entry.getConfigValue());
        assertEquals(3, entry.getVersion());
        assertEquals("desc", entry.getDescription());
        assertEquals(now, entry.getCreatedAt());
        assertEquals(now, entry.getUpdatedAt());
        assertEquals(createdBy, entry.getCreatedBy());
    }

    @Test
    void should_supportSetters() {
        ConfigEntry entry = new ConfigEntry();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        entry.setId(id);
        entry.setTenantId(tenantId);
        entry.setConfigKey("key");
        entry.setConfigValue("value");
        entry.setVersion(2);
        entry.setDescription("Updated");

        assertEquals(id, entry.getId());
        assertEquals(tenantId, entry.getTenantId());
        assertEquals("key", entry.getConfigKey());
        assertEquals("value", entry.getConfigValue());
        assertEquals(2, entry.getVersion());
        assertEquals("Updated", entry.getDescription());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ConfigEntry e1 = ConfigEntry.builder().id(id).tenantId(tenantId).configKey("k").configValue("v").build();
        ConfigEntry e2 = ConfigEntry.builder().id(id).tenantId(tenantId).configKey("k").configValue("v").build();

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void should_supportToString() {
        ConfigEntry entry = ConfigEntry.builder().configKey("test").configValue("val").build();
        assertNotNull(entry.toString());
        assert entry.toString().contains("test");
    }
}
