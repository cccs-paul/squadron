package com.squadron.common.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditQueryServiceTest {

    private AuditQueryService queryService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        queryService = new AuditQueryService();
        tenantId = UUID.randomUUID();
    }

    @Test
    void should_findByTenantId_when_eventsExist() {
        AuditEvent event1 = createEvent(tenantId, UUID.randomUUID(), "TASK", "1");
        AuditEvent event2 = createEvent(tenantId, UUID.randomUUID(), "TASK", "2");
        AuditEvent event3 = createEvent(UUID.randomUUID(), UUID.randomUUID(), "TASK", "3");

        queryService.store(event1);
        queryService.store(event2);
        queryService.store(event3);

        List<AuditEvent> results = queryService.findByTenantId(tenantId, 0, 50);
        assertEquals(2, results.size());
    }

    @Test
    void should_findByUserId_when_eventsExist() {
        UUID userId = UUID.randomUUID();
        AuditEvent event1 = createEvent(tenantId, userId, "TASK", "1");
        AuditEvent event2 = createEvent(tenantId, UUID.randomUUID(), "TASK", "2");
        AuditEvent event3 = createEvent(tenantId, userId, "CONFIG", "3");

        queryService.store(event1);
        queryService.store(event2);
        queryService.store(event3);

        List<AuditEvent> results = queryService.findByUserId(tenantId, userId, 0, 50);
        assertEquals(2, results.size());
    }

    @Test
    void should_findByResourceType_when_eventsExist() {
        AuditEvent event1 = createEvent(tenantId, UUID.randomUUID(), "TASK", "1");
        AuditEvent event2 = createEvent(tenantId, UUID.randomUUID(), "CONFIG", "2");
        AuditEvent event3 = createEvent(tenantId, UUID.randomUUID(), "TASK", "3");

        queryService.store(event1);
        queryService.store(event2);
        queryService.store(event3);

        List<AuditEvent> results = queryService.findByResourceType(tenantId, "TASK", 0, 50);
        assertEquals(2, results.size());
    }

    @Test
    void should_findByResourceId_when_eventsExist() {
        AuditEvent event1 = createEvent(tenantId, UUID.randomUUID(), "TASK", "task-1");
        AuditEvent event2 = createEvent(tenantId, UUID.randomUUID(), "TASK", "task-2");
        AuditEvent event3 = createEvent(tenantId, UUID.randomUUID(), "TASK", "task-1");

        queryService.store(event1);
        queryService.store(event2);
        queryService.store(event3);

        List<AuditEvent> results = queryService.findByResourceId(tenantId, "TASK", "task-1");
        assertEquals(2, results.size());
    }

    @Test
    void should_findByDateRange_when_eventsInRange() {
        Instant base = Instant.parse("2025-06-01T00:00:00Z");

        AuditEvent event1 = createEventWithTimestamp(tenantId, base.minusSeconds(3600));
        AuditEvent event2 = createEventWithTimestamp(tenantId, base);
        AuditEvent event3 = createEventWithTimestamp(tenantId, base.plusSeconds(3600));
        AuditEvent event4 = createEventWithTimestamp(tenantId, base.plusSeconds(7200));

        queryService.store(event1);
        queryService.store(event2);
        queryService.store(event3);
        queryService.store(event4);

        List<AuditEvent> results = queryService.findByDateRange(
                tenantId, base, base.plusSeconds(3600), 0, 50);
        assertEquals(2, results.size());
    }

    @Test
    void should_returnEmptyList_when_noMatchingEvents() {
        queryService.store(createEvent(UUID.randomUUID(), UUID.randomUUID(), "TASK", "1"));

        List<AuditEvent> results = queryService.findByTenantId(tenantId, 0, 50);
        assertTrue(results.isEmpty());
    }

    @Test
    void should_paginate_correctly() {
        for (int i = 0; i < 10; i++) {
            queryService.store(createEvent(tenantId, UUID.randomUUID(), "TASK", "id-" + i));
        }

        List<AuditEvent> page0 = queryService.findByTenantId(tenantId, 0, 3);
        assertEquals(3, page0.size());

        List<AuditEvent> page1 = queryService.findByTenantId(tenantId, 1, 3);
        assertEquals(3, page1.size());

        List<AuditEvent> page3 = queryService.findByTenantId(tenantId, 3, 3);
        assertEquals(1, page3.size());

        List<AuditEvent> pageBeyond = queryService.findByTenantId(tenantId, 10, 3);
        assertTrue(pageBeyond.isEmpty());
    }

    @Test
    void should_handleNegativePageGracefully() {
        queryService.store(createEvent(tenantId, UUID.randomUUID(), "TASK", "1"));

        List<AuditEvent> results = queryService.findByTenantId(tenantId, -1, 50);
        assertEquals(1, results.size());
    }

    @Test
    void should_handleZeroOrNegativeSize_withDefault() {
        queryService.store(createEvent(tenantId, UUID.randomUUID(), "TASK", "1"));

        List<AuditEvent> results = queryService.findByTenantId(tenantId, 0, 0);
        assertEquals(1, results.size());

        List<AuditEvent> results2 = queryService.findByTenantId(tenantId, 0, -5);
        assertEquals(1, results2.size());
    }

    @Test
    void should_evictOldestEvents_when_bufferFull() {
        // Fill buffer to max
        for (int i = 0; i < AuditQueryService.MAX_BUFFER_SIZE + 5; i++) {
            queryService.store(createEvent(tenantId, UUID.randomUUID(), "TASK", "id-" + i));
        }

        assertEquals(AuditQueryService.MAX_BUFFER_SIZE, queryService.size());
    }

    @Test
    void should_returnEmptyList_when_nullTenantId() {
        queryService.store(createEvent(tenantId, UUID.randomUUID(), "TASK", "1"));

        assertTrue(queryService.findByTenantId(null, 0, 50).isEmpty());
        assertTrue(queryService.findByUserId(null, UUID.randomUUID(), 0, 50).isEmpty());
        assertTrue(queryService.findByResourceType(null, "TASK", 0, 50).isEmpty());
        assertTrue(queryService.findByResourceId(null, "TASK", "1").isEmpty());
        assertTrue(queryService.findByDateRange(null, Instant.now(), Instant.now(), 0, 50).isEmpty());
    }

    @Test
    void should_notStoreNullEvent() {
        queryService.store(null);
        assertEquals(0, queryService.size());
    }

    @Test
    void should_returnEmptyList_when_nullUserId() {
        assertTrue(queryService.findByUserId(tenantId, null, 0, 50).isEmpty());
    }

    @Test
    void should_returnEmptyList_when_nullResourceType() {
        assertTrue(queryService.findByResourceType(tenantId, null, 0, 50).isEmpty());
        assertTrue(queryService.findByResourceId(tenantId, null, "1").isEmpty());
    }

    @Test
    void should_returnEmptyList_when_nullResourceId() {
        assertTrue(queryService.findByResourceId(tenantId, "TASK", null).isEmpty());
    }

    @Test
    void should_returnEmptyList_when_nullDateRange() {
        assertTrue(queryService.findByDateRange(tenantId, null, Instant.now(), 0, 50).isEmpty());
        assertTrue(queryService.findByDateRange(tenantId, Instant.now(), null, 0, 50).isEmpty());
    }

    private AuditEvent createEvent(UUID tenantId, UUID userId, String resourceType, String resourceId) {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .action("TEST_ACTION")
                .resourceType(resourceType)
                .resourceId(resourceId)
                .auditAction(AuditAction.CREATE)
                .timestamp(Instant.now())
                .build();
    }

    private AuditEvent createEventWithTimestamp(UUID tenantId, Instant timestamp) {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .action("TEST_ACTION")
                .resourceType("TASK")
                .resourceId("resource-1")
                .auditAction(AuditAction.CREATE)
                .timestamp(timestamp)
                .build();
    }
}
