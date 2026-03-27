package com.squadron.common.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {

    @Test
    void should_buildAuditEvent_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEvent event = AuditEvent.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(userId)
                .username("testuser@example.com")
                .action("TASK_CREATED")
                .resourceType("TASK")
                .resourceId("task-123")
                .details("{\"priority\": \"HIGH\"}")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .sourceService("squadron-orchestrator")
                .auditAction(AuditAction.CREATE)
                .timestamp(now)
                .build();

        assertEquals(id, event.getId());
        assertEquals(tenantId, event.getTenantId());
        assertEquals(userId, event.getUserId());
        assertEquals("testuser@example.com", event.getUsername());
        assertEquals("TASK_CREATED", event.getAction());
        assertEquals("TASK", event.getResourceType());
        assertEquals("task-123", event.getResourceId());
        assertEquals("{\"priority\": \"HIGH\"}", event.getDetails());
        assertEquals("192.168.1.1", event.getIpAddress());
        assertEquals("Mozilla/5.0", event.getUserAgent());
        assertEquals("squadron-orchestrator", event.getSourceService());
        assertEquals(AuditAction.CREATE, event.getAuditAction());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void should_createEmptyAuditEvent_when_usingNoArgsConstructor() {
        AuditEvent event = new AuditEvent();

        assertNull(event.getId());
        assertNull(event.getTenantId());
        assertNull(event.getUserId());
        assertNull(event.getUsername());
        assertNull(event.getAction());
        assertNull(event.getResourceType());
        assertNull(event.getResourceId());
        assertNull(event.getDetails());
        assertNull(event.getIpAddress());
        assertNull(event.getUserAgent());
        assertNull(event.getSourceService());
        assertNull(event.getAuditAction());
        assertNull(event.getTimestamp());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AuditEvent event = new AuditEvent();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        event.setId(id);
        event.setTenantId(tenantId);
        event.setAction("CONFIG_UPDATED");
        event.setResourceType("CONFIG");
        event.setAuditAction(AuditAction.UPDATE);

        assertEquals(id, event.getId());
        assertEquals(tenantId, event.getTenantId());
        assertEquals("CONFIG_UPDATED", event.getAction());
        assertEquals("CONFIG", event.getResourceType());
        assertEquals(AuditAction.UPDATE, event.getAuditAction());
    }

    @Test
    void should_createAuditEvent_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEvent event = new AuditEvent(id, tenantId, userId, "user@test.com",
                "PR_CREATED", "PULL_REQUEST", "pr-456", "{}", "10.0.0.1",
                "curl/7.68", "squadron-git", AuditAction.CREATE, now);

        assertEquals(id, event.getId());
        assertEquals(tenantId, event.getTenantId());
        assertEquals(userId, event.getUserId());
        assertEquals("user@test.com", event.getUsername());
        assertEquals("PR_CREATED", event.getAction());
        assertEquals("PULL_REQUEST", event.getResourceType());
        assertEquals("pr-456", event.getResourceId());
        assertEquals("{}", event.getDetails());
        assertEquals("10.0.0.1", event.getIpAddress());
        assertEquals("curl/7.68", event.getUserAgent());
        assertEquals("squadron-git", event.getSourceService());
        assertEquals(AuditAction.CREATE, event.getAuditAction());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEvent event1 = AuditEvent.builder()
                .id(id).tenantId(tenantId).action("TEST").timestamp(now).build();
        AuditEvent event2 = AuditEvent.builder()
                .id(id).tenantId(tenantId).action("TEST").timestamp(now).build();

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        AuditEvent event1 = AuditEvent.builder()
                .id(UUID.randomUUID()).action("TEST_A").build();
        AuditEvent event2 = AuditEvent.builder()
                .id(UUID.randomUUID()).action("TEST_B").build();

        assertNotEquals(event1, event2);
    }

    @Test
    void should_returnNonNullString_when_callingToString() {
        AuditEvent event = AuditEvent.builder()
                .action("TEST_ACTION")
                .resourceType("RESOURCE")
                .build();

        String str = event.toString();
        assertNotNull(str);
        assertTrue(str.contains("TEST_ACTION"));
        assertTrue(str.contains("RESOURCE"));
    }

    @Test
    void should_handleNullValues_when_usingBuilder() {
        AuditEvent event = AuditEvent.builder()
                .id(null)
                .tenantId(null)
                .userId(null)
                .action(null)
                .build();

        assertNull(event.getId());
        assertNull(event.getTenantId());
        assertNull(event.getUserId());
        assertNull(event.getAction());
    }
}
