package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.adapter.PlatformAdapterRegistry;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformSyncServiceTest {

    @Mock
    private PlatformAdapterRegistry adapterRegistry;

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private UserTokenService userTokenService;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private TicketingPlatformAdapter adapter;

    private ObjectMapper objectMapper;
    private PlatformSyncService syncService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncService = new PlatformSyncService(
                adapterRegistry, connectionRepository, userTokenService,
                encryptionService, natsEventPublisher, objectMapper);
    }

    @Test
    void should_syncTasks_when_validConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .build();

        List<PlatformTaskDto> expectedTasks = List.of(
                PlatformTaskDto.builder().externalId("PROJ-1").title("Task 1").build(),
                PlatformTaskDto.builder().externalId("PROJ-2").title("Task 2").build()
        );

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.fetchTasks(anyString(), any())).thenReturn(expectedTasks);
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        List<PlatformTaskDto> result = syncService.syncTasks(connectionId, "PROJ");

        assertEquals(2, result.size());
        verify(adapter).configure("https://example.atlassian.net", "plain-token");
        verify(natsEventPublisher).publish(anyString(), any());
    }

    @Test
    void should_throwNotFound_when_syncTasksConnectionMissing() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> syncService.syncTasks(connectionId, "PROJ"));
    }

    @Test
    void should_throwUnsupported_when_adapterNotImplemented() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.fetchTasks(anyString(), any())).thenThrow(new UnsupportedOperationException("Not yet implemented"));

        assertThrows(UnsupportedOperationException.class,
                () -> syncService.syncTasks(connectionId, "PROJ"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_syncFails() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.fetchTasks(anyString(), any())).thenThrow(new RuntimeException("API error"));

        assertThrows(PlatformIntegrationException.class,
                () -> syncService.syncTasks(connectionId, "PROJ"));
    }

    @Test
    void should_pushTaskStatus_withUserToken() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(tenantId)
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId)).thenReturn("user-token");
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(adapter);
        doNothing().when(adapter).updateTaskStatus(anyString(), anyString(), anyString());
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        syncService.pushTaskStatus(connectionId, "owner/repo#1", "closed", "Done", userId);

        verify(adapter).configure("https://api.github.com", "user-token");
        verify(adapter).updateTaskStatus("owner/repo#1", "closed", "Done");
        verify(natsEventPublisher).publish(anyString(), any());
    }

    @Test
    void should_pushTaskStatus_withConnectionToken_when_noUserToken() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenThrow(new ResourceNotFoundException("UserPlatformToken", "not found"));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("connection-token");
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(adapter);
        doNothing().when(adapter).updateTaskStatus(anyString(), anyString(), anyString());
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        syncService.pushTaskStatus(connectionId, "owner/repo#1", "closed", "Done", userId);

        verify(adapter).configure("https://api.github.com", "connection-token");
    }

    @Test
    void should_pushTaskStatus_withConnectionToken_when_noUserId() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("connection-token");
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        doNothing().when(adapter).updateTaskStatus(anyString(), anyString(), any());
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        syncService.pushTaskStatus(connectionId, "PROJ-1", "Done", null, null);

        verify(adapter).configure("https://example.atlassian.net", "connection-token");
        verify(userTokenService, never()).getDecryptedAccessToken(any(), any());
    }

    @Test
    void should_throwNotFound_when_pushStatusConnectionMissing() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> syncService.pushTaskStatus(connectionId, "PROJ-1", "Done", null, null));
    }

    @Test
    void should_throwPlatformIntegrationException_when_pushStatusFails() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("token");
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        doThrow(new RuntimeException("API error"))
                .when(adapter).updateTaskStatus(anyString(), anyString(), any());

        assertThrows(PlatformIntegrationException.class,
                () -> syncService.pushTaskStatus(connectionId, "PROJ-1", "Done", null, null));
    }

    @Test
    void should_returnEmptyToken_when_noCredentials() throws Exception {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(null)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        doNothing().when(adapter).updateTaskStatus(anyString(), anyString(), any());
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        syncService.pushTaskStatus(connectionId, "PROJ-1", "Done", null, null);

        verify(adapter).configure("https://example.atlassian.net", "");
    }
}
