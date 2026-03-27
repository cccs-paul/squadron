package com.squadron.notification.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendNotificationRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        SendNotificationRequest request = SendNotificationRequest.builder()
                .tenantId(tenantId)
                .userId(userId)
                .channel("EMAIL")
                .subject("Test Subject")
                .body("Test Body")
                .relatedTaskId(taskId)
                .eventType("TASK_STATE_CHANGED")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(userId, request.getUserId());
        assertEquals("EMAIL", request.getChannel());
        assertEquals("Test Subject", request.getSubject());
        assertEquals("Test Body", request.getBody());
        assertEquals(taskId, request.getRelatedTaskId());
        assertEquals("TASK_STATE_CHANGED", request.getEventType());
    }

    @Test
    void should_useNoArgsConstructor() {
        SendNotificationRequest request = new SendNotificationRequest();
        assertNull(request.getTenantId());
        assertNull(request.getChannel());
    }

    @Test
    void should_supportSetters() {
        SendNotificationRequest request = new SendNotificationRequest();
        UUID tenantId = UUID.randomUUID();
        request.setTenantId(tenantId);
        request.setChannel("SLACK");

        assertEquals(tenantId, request.getTenantId());
        assertEquals("SLACK", request.getChannel());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        SendNotificationRequest request = new SendNotificationRequest(
                tenantId, userId, "TEAMS", "Subject", "Body", taskId, "REVIEW_UPDATED"
        );

        assertEquals(tenantId, request.getTenantId());
        assertEquals(userId, request.getUserId());
        assertEquals("TEAMS", request.getChannel());
        assertEquals("Subject", request.getSubject());
        assertEquals("Body", request.getBody());
        assertEquals(taskId, request.getRelatedTaskId());
        assertEquals("REVIEW_UPDATED", request.getEventType());
    }
}
