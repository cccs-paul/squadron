package com.squadron.config.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigAuditLogTest {

    @Test
    void should_buildAuditLog_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID configEntryId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();

        ConfigAuditLog log = ConfigAuditLog.builder()
                .tenantId(tenantId)
                .configEntryId(configEntryId)
                .configKey("max.retries")
                .previousValue("3")
                .newValue("5")
                .changedBy(changedBy)
                .build();

        assertEquals(tenantId, log.getTenantId());
        assertEquals(configEntryId, log.getConfigEntryId());
        assertEquals("max.retries", log.getConfigKey());
        assertEquals("3", log.getPreviousValue());
        assertEquals("5", log.getNewValue());
        assertEquals(changedBy, log.getChangedBy());
    }

    @Test
    void should_allowNullPreviousValue_when_firstCreation() {
        ConfigAuditLog log = ConfigAuditLog.builder()
                .tenantId(UUID.randomUUID())
                .configEntryId(UUID.randomUUID())
                .configKey("key")
                .previousValue(null)
                .newValue("value")
                .build();

        assertNull(log.getPreviousValue());
        assertEquals("value", log.getNewValue());
    }

    @Test
    void should_setChangedAt_when_onCreateCalled() {
        ConfigAuditLog log = ConfigAuditLog.builder()
                .tenantId(UUID.randomUUID())
                .configEntryId(UUID.randomUUID())
                .configKey("key")
                .newValue("value")
                .build();

        log.onCreate();

        assertNotNull(log.getChangedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        ConfigAuditLog log = new ConfigAuditLog();
        assertNull(log.getId());
        assertNull(log.getTenantId());
        assertNull(log.getConfigKey());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID configEntryId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigAuditLog log = new ConfigAuditLog(id, tenantId, configEntryId,
                "key", "old", "new", changedBy, now);

        assertEquals(id, log.getId());
        assertEquals(tenantId, log.getTenantId());
        assertEquals(configEntryId, log.getConfigEntryId());
        assertEquals("key", log.getConfigKey());
        assertEquals("old", log.getPreviousValue());
        assertEquals("new", log.getNewValue());
        assertEquals(changedBy, log.getChangedBy());
        assertEquals(now, log.getChangedAt());
    }

    @Test
    void should_supportSetters() {
        ConfigAuditLog log = new ConfigAuditLog();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID configEntryId = UUID.randomUUID();

        log.setId(id);
        log.setTenantId(tenantId);
        log.setConfigEntryId(configEntryId);
        log.setConfigKey("key");
        log.setPreviousValue("old");
        log.setNewValue("new");

        assertEquals(id, log.getId());
        assertEquals(tenantId, log.getTenantId());
        assertEquals(configEntryId, log.getConfigEntryId());
        assertEquals("key", log.getConfigKey());
        assertEquals("old", log.getPreviousValue());
        assertEquals("new", log.getNewValue());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID configEntryId = UUID.randomUUID();

        ConfigAuditLog l1 = ConfigAuditLog.builder()
                .id(id).tenantId(tenantId).configEntryId(configEntryId)
                .configKey("k").newValue("v").build();
        ConfigAuditLog l2 = ConfigAuditLog.builder()
                .id(id).tenantId(tenantId).configEntryId(configEntryId)
                .configKey("k").newValue("v").build();

        assertEquals(l1, l2);
        assertEquals(l1.hashCode(), l2.hashCode());
    }

    @Test
    void should_supportToString() {
        ConfigAuditLog log = ConfigAuditLog.builder().configKey("test").newValue("val").build();
        assertNotNull(log.toString());
        assert log.toString().contains("test");
    }
}
