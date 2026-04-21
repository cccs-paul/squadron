package com.squadron.agent.ephemeral;

import com.squadron.agent.client.ResilientWorkspaceServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EphemeralContainerServiceTest {

    @Mock
    private ResilientWorkspaceServiceClient workspaceClient;

    private EphemeralContainerConfig config;
    private EphemeralContainerService service;

    @BeforeEach
    void setUp() {
        config = new EphemeralContainerConfig();
        config.setHealthCheckTimeoutSeconds(4);
        config.setHealthCheckIntervalSeconds(1);
        service = new EphemeralContainerService(workspaceClient, config);
    }

    @Test
    void should_returnNullClient_whenNoActiveContainer() {
        UUID sessionId = UUID.randomUUID();
        assertNull(service.getClient(sessionId));
    }

    @Test
    void should_returnNullWorkspaceId_whenNoActiveContainer() {
        UUID sessionId = UUID.randomUUID();
        assertNull(service.getWorkspaceId(sessionId));
    }

    @Test
    void should_returnFalse_whenNoContainerActive() {
        assertFalse(service.isContainerActive(UUID.randomUUID()));
    }

    @Test
    void should_returnZero_whenNoActiveContainers() {
        assertEquals(0, service.getActiveContainerCount());
    }

    @Test
    void should_stopContainer_gracefully_whenNoContainerExists() {
        UUID sessionId = UUID.randomUUID();
        assertDoesNotThrow(() -> service.stopContainer(sessionId));
        verify(workspaceClient, never()).destroyWorkspace(anyString());
    }

    @Test
    void should_throwException_whenWorkspaceCreationFails() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(workspaceClient.createWorkspace(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        assertThrows(RuntimeException.class,
                () -> service.startContainer(sessionId, tenantId,
                        "openai", "gpt-4o", null, "key", "CLOUD", null));
    }

    @Test
    void should_throwException_whenWorkspaceReturnsNoData() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(workspaceClient.createWorkspace(any()))
                .thenReturn(Map.of("status", "ok"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.startContainer(sessionId, tenantId,
                        "openai", "gpt-4o", null, "key", "CLOUD", null));
        assertTrue(ex.getMessage().contains("Workspace creation returned no data"));
    }

    @Test
    void should_cleanupContainer_whenConfigInjectionFails() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String workspaceId = UUID.randomUUID().toString();

        when(workspaceClient.createWorkspace(any()))
                .thenReturn(Map.of("data", Map.of("id", workspaceId)));
        doThrow(new RuntimeException("Write failed"))
                .when(workspaceClient).writeFile(anyString(), anyString(), any(byte[].class));

        assertThrows(RuntimeException.class,
                () -> service.startContainer(sessionId, tenantId,
                        "openai", "gpt-4o", null, "key", "CLOUD", null));

        verify(workspaceClient).destroyWorkspace(workspaceId);
    }

    @Test
    void should_cleanupContainer_whenHealthCheckTimesOut() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String workspaceId = UUID.randomUUID().toString();

        // Workspace created successfully
        when(workspaceClient.createWorkspace(any()))
                .thenReturn(Map.of("data", Map.of("id", workspaceId)));
        // Config injected, server started
        doNothing().when(workspaceClient).writeFile(anyString(), anyString(), any(byte[].class));
        when(workspaceClient.exec(eq(workspaceId), any()))
                // First exec: start server (returns empty)
                .thenReturn(Map.of("data", Map.of("exitCode", 0, "stdout", "")))
                // Second exec: hostname -i
                .thenReturn(Map.of("data", Map.of("exitCode", 0, "stdout", "10.0.0.5")));

        // Health check always fails -> times out
        // The client will be created with IP 10.0.0.5 but health checks fail
        // since we can't mock the internal HttpClient, the health check will
        // throw connection refused and return false, causing timeout
        config.setHealthCheckTimeoutSeconds(2);
        config.setHealthCheckIntervalSeconds(1);

        assertThrows(RuntimeException.class,
                () -> service.startContainer(sessionId, tenantId,
                        "openai", "gpt-4o", null, "key", "CLOUD", null));

        verify(workspaceClient).destroyWorkspace(workspaceId);
    }

    @Test
    void should_stopContainer_andDestroyWorkspace() {
        UUID sessionId = UUID.randomUUID();
        String workspaceId = "ws-123";

        // Manually add a container to the active map
        OpenCodeContainerClient mockClient = mock(OpenCodeContainerClient.class);
        service.getActiveContainers().put(sessionId,
                new EphemeralContainerService.ContainerInfo(workspaceId, "10.0.0.1", mockClient));

        assertTrue(service.isContainerActive(sessionId));
        assertEquals(1, service.getActiveContainerCount());

        service.stopContainer(sessionId);

        assertFalse(service.isContainerActive(sessionId));
        assertEquals(0, service.getActiveContainerCount());
        verify(workspaceClient).destroyWorkspace(workspaceId);
    }

    @Test
    void should_returnClient_whenContainerIsActive() {
        UUID sessionId = UUID.randomUUID();
        OpenCodeContainerClient mockClient = mock(OpenCodeContainerClient.class);
        service.getActiveContainers().put(sessionId,
                new EphemeralContainerService.ContainerInfo("ws-1", "10.0.0.1", mockClient));

        assertSame(mockClient, service.getClient(sessionId));
    }

    @Test
    void should_returnWorkspaceId_whenContainerIsActive() {
        UUID sessionId = UUID.randomUUID();
        OpenCodeContainerClient mockClient = mock(OpenCodeContainerClient.class);
        service.getActiveContainers().put(sessionId,
                new EphemeralContainerService.ContainerInfo("ws-abc", "10.0.0.1", mockClient));

        assertEquals("ws-abc", service.getWorkspaceId(sessionId));
    }

    @Test
    void should_handleDestroyFailure_gracefully() {
        UUID sessionId = UUID.randomUUID();
        OpenCodeContainerClient mockClient = mock(OpenCodeContainerClient.class);
        service.getActiveContainers().put(sessionId,
                new EphemeralContainerService.ContainerInfo("ws-1", "10.0.0.1", mockClient));

        doThrow(new RuntimeException("Destroy failed"))
                .when(workspaceClient).destroyWorkspace("ws-1");

        // Should not throw
        assertDoesNotThrow(() -> service.stopContainer(sessionId));
        assertFalse(service.isContainerActive(sessionId));
    }

    @Test
    void should_throwException_whenIpResolutionFails() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String workspaceId = UUID.randomUUID().toString();

        when(workspaceClient.createWorkspace(any()))
                .thenReturn(Map.of("data", Map.of("id", workspaceId)));
        doNothing().when(workspaceClient).writeFile(anyString(), anyString(), any(byte[].class));
        when(workspaceClient.exec(eq(workspaceId), any()))
                .thenReturn(Map.of("data", Map.of("exitCode", 0, "stdout", "")))
                .thenReturn(Map.of("data", Map.of("exitCode", 1, "stdout", "")));

        assertThrows(RuntimeException.class,
                () -> service.startContainer(sessionId, tenantId,
                        "openai", "gpt-4o", null, "key", "CLOUD", null));

        verify(workspaceClient).destroyWorkspace(workspaceId);
    }
}
