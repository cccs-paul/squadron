package com.squadron.notification.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationTest {

    @Test
    void should_buildNotification_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .userId(userId)
                .channel("EMAIL")
                .subject("Test Subject")
                .body("Test Body")
                .relatedTaskId(taskId)
                .eventType("TASK_STATE_CHANGED")
                .build();

        assertEquals(tenantId, notification.getTenantId());
        assertEquals(userId, notification.getUserId());
        assertEquals("EMAIL", notification.getChannel());
        assertEquals("Test Subject", notification.getSubject());
        assertEquals("Test Body", notification.getBody());
        assertEquals("PENDING", notification.getStatus());
        assertEquals(taskId, notification.getRelatedTaskId());
        assertEquals("TASK_STATE_CHANGED", notification.getEventType());
        assertEquals(0, notification.getRetryCount());
    }

    @Test
    void should_setDefaultStatus_when_notExplicitlySet() {
        Notification notification = Notification.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .build();

        assertEquals("PENDING", notification.getStatus());
    }

    @Test
    void should_setCreatedAt_when_onCreateCalled() {
        Notification notification = Notification.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .build();

        notification.onCreate();

        assertNotNull(notification.getCreatedAt());
        assertEquals("PENDING", notification.getStatus());
    }

    @Test
    void should_notOverrideCreatedAt_when_alreadySet() {
        Instant fixedTime = Instant.parse("2025-01-01T00:00:00Z");
        Notification notification = Notification.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .createdAt(fixedTime)
                .build();

        notification.onCreate();

        assertEquals(fixedTime, notification.getCreatedAt());
    }

    @Test
    void should_setStatusToPending_when_statusIsNullOnCreate() {
        Notification notification = new Notification();
        notification.setTenantId(UUID.randomUUID());
        notification.setUserId(UUID.randomUUID());
        notification.setChannel("IN_APP");
        notification.setSubject("Test");
        notification.setBody("Body");
        notification.setStatus(null);

        notification.onCreate();

        assertEquals("PENDING", notification.getStatus());
    }

    @Test
    void should_useNoArgsConstructor() {
        Notification notification = new Notification();
        assertNull(notification.getId());
        assertNull(notification.getTenantId());
        assertNull(notification.getUserId());
        assertNull(notification.getChannel());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        Notification notification = new Notification(
                id, tenantId, userId, "EMAIL", "Subject", "Body",
                "SENT", taskId, "TASK_STATE_CHANGED", null, now, now, 0, null
        );

        assertEquals(id, notification.getId());
        assertEquals(tenantId, notification.getTenantId());
        assertEquals(userId, notification.getUserId());
        assertEquals("EMAIL", notification.getChannel());
        assertEquals("Subject", notification.getSubject());
        assertEquals("Body", notification.getBody());
        assertEquals("SENT", notification.getStatus());
        assertEquals(taskId, notification.getRelatedTaskId());
        assertEquals("TASK_STATE_CHANGED", notification.getEventType());
        assertEquals(now, notification.getCreatedAt());
        assertEquals(now, notification.getSentAt());
        assertEquals(0, notification.getRetryCount());
    }

    @Test
    void should_supportSetters() {
        Notification notification = new Notification();
        UUID id = UUID.randomUUID();
        notification.setId(id);
        notification.setStatus("READ");
        notification.setReadAt(Instant.now());
        notification.setErrorMessage("Some error");

        assertEquals(id, notification.getId());
        assertEquals("READ", notification.getStatus());
        assertNotNull(notification.getReadAt());
        assertEquals("Some error", notification.getErrorMessage());
    }
}
