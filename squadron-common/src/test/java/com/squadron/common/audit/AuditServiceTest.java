package com.squadron.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private Connection natsConnection;

    private ObjectMapper objectMapper;
    private AuditProperties properties;
    private AuditQueryService auditQueryService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        properties = new AuditProperties();
        auditQueryService = new AuditQueryService();
        auditService = new AuditService(properties, objectMapper, natsConnection, auditQueryService);
    }

    @Test
    void should_logEvent_when_fullAuditEventProvided() {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .username("user@test.com")
                .action("TASK_CREATED")
                .resourceType("TASK")
                .resourceId("task-1")
                .auditAction(AuditAction.CREATE)
                .timestamp(Instant.now())
                .build();

        auditService.logEvent(event);

        verify(natsConnection).publish(eq("squadron.audit.events"), any(byte[].class));
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_logEvent_when_usingConvenienceMethod() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        auditService.logEvent(tenantId, userId, "CONFIG_UPDATED", "CONFIG",
                "config-1", AuditAction.UPDATE, "{\"key\":\"value\"}");

        verify(natsConnection).publish(eq("squadron.audit.events"), any(byte[].class));
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_notLogEvent_when_auditDisabled() {
        properties.setEnabled(false);
        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .build();

        auditService.logEvent(event);

        verifyNoInteractions(natsConnection);
        assertEquals(0, auditQueryService.size());
    }

    @Test
    void should_notPublishToNats_when_natsPublishDisabled() {
        properties.setPublishToNats(false);
        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .timestamp(Instant.now())
                .build();

        auditService.logEvent(event);

        verifyNoInteractions(natsConnection);
        // Should still be stored locally
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_handleNullEvent_gracefully() {
        auditService.logEvent((AuditEvent) null);

        verifyNoInteractions(natsConnection);
        assertEquals(0, auditQueryService.size());
    }

    @Test
    void should_skipExcludedActions() {
        properties.setExcludedActions(List.of("HEALTH_CHECK", "PING"));
        AuditEvent event = AuditEvent.builder()
                .action("HEALTH_CHECK")
                .auditAction(AuditAction.EXECUTE)
                .build();

        auditService.logEvent(event);

        verifyNoInteractions(natsConnection);
        assertEquals(0, auditQueryService.size());
    }

    @Test
    void should_logEvent_when_actionNotExcluded() {
        properties.setExcludedActions(List.of("HEALTH_CHECK"));
        AuditEvent event = AuditEvent.builder()
                .action("TASK_CREATED")
                .auditAction(AuditAction.CREATE)
                .timestamp(Instant.now())
                .build();

        auditService.logEvent(event);

        verify(natsConnection).publish(eq("squadron.audit.events"), any(byte[].class));
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_assignIdAndTimestamp_when_notSet() {
        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .build();

        assertNull(event.getId());
        assertNull(event.getTimestamp());

        auditService.logEvent(event);

        assertNotNull(event.getId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_notOverrideId_when_alreadySet() {
        UUID originalId = UUID.randomUUID();
        Instant originalTime = Instant.parse("2025-01-01T00:00:00Z");
        AuditEvent event = AuditEvent.builder()
                .id(originalId)
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .timestamp(originalTime)
                .build();

        auditService.logEvent(event);

        assertEquals(originalId, event.getId());
        assertEquals(originalTime, event.getTimestamp());
    }

    @Test
    void should_handleNatsFailure_gracefully() {
        doThrow(new RuntimeException("NATS down")).when(natsConnection)
                .publish(anyString(), any(byte[].class));

        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .timestamp(Instant.now())
                .build();

        // Should not throw
        assertDoesNotThrow(() -> auditService.logEvent(event));
        // Should still be stored locally
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_useCustomNatsSubject() {
        properties.setNatsSubject("custom.audit.subject");
        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .timestamp(Instant.now())
                .build();

        auditService.logEvent(event);

        verify(natsConnection).publish(eq("custom.audit.subject"), any(byte[].class));
    }

    @Test
    void should_handleNullNatsConnection_gracefully() {
        AuditService serviceWithoutNats = new AuditService(properties, objectMapper, null, auditQueryService);
        AuditEvent event = AuditEvent.builder()
                .action("TEST")
                .auditAction(AuditAction.EXECUTE)
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> serviceWithoutNats.logEvent(event));
        assertEquals(1, auditQueryService.size());
    }

    @Test
    void should_handleNullActionInExclusionCheck() {
        properties.setExcludedActions(List.of("SOMETHING"));
        AuditEvent event = AuditEvent.builder()
                .action(null)
                .auditAction(AuditAction.EXECUTE)
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> auditService.logEvent(event));
        assertEquals(1, auditQueryService.size());
    }
}
