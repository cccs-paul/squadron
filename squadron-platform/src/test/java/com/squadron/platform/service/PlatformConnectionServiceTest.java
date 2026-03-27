package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.adapter.PlatformAdapterRegistry;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.dto.CreateConnectionRequest;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class PlatformConnectionServiceTest {

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private PlatformAdapterRegistry adapterRegistry;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private TicketingPlatformAdapter adapter;

    private ObjectMapper objectMapper;
    private PlatformConnectionService connectionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        connectionService = new PlatformConnectionService(
                connectionRepository, adapterRegistry, encryptionService, objectMapper);
    }

    @Test
    void should_createConnection_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .credentials(Map.of("clientId", "id123", "clientSecret", "secret"))
                .metadata(Map.of("env", "prod"))
                .build();

        when(encryptionService.encrypt("secret")).thenReturn("encrypted-secret");

        PlatformConnection savedConnection = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .status("ACTIVE")
                .build();

        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        PlatformConnection result = connectionService.createConnection(request);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("JIRA_CLOUD", result.getPlatformType());
        verify(connectionRepository).save(any(PlatformConnection.class));
        verify(encryptionService).encrypt("secret");
    }

    @Test
    void should_createConnection_when_noCredentials() {
        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("PAT")
                .credentials(null)
                .build();

        PlatformConnection savedConnection = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(request.getTenantId())
                .platformType("GITHUB")
                .status("ACTIVE")
                .build();

        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        PlatformConnection result = connectionService.createConnection(request);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void should_getConnection_when_exists() {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        PlatformConnection result = connectionService.getConnection(connectionId);

        assertEquals(connectionId, result.getId());
    }

    @Test
    void should_throwNotFound_when_connectionMissing() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionService.getConnection(connectionId));
    }

    @Test
    void should_listConnectionsByTenant() {
        UUID tenantId = UUID.randomUUID();
        PlatformConnection c1 = PlatformConnection.builder().id(UUID.randomUUID()).tenantId(tenantId).build();
        PlatformConnection c2 = PlatformConnection.builder().id(UUID.randomUUID()).tenantId(tenantId).build();

        when(connectionRepository.findByTenantId(tenantId)).thenReturn(List.of(c1, c2));

        List<PlatformConnection> result = connectionService.listConnectionsByTenant(tenantId);

        assertEquals(2, result.size());
    }

    @Test
    void should_updateConnection_when_exists() {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection existing = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://old.atlassian.net")
                .authType("OAUTH2")
                .build();

        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(existing.getTenantId())
                .platformType("JIRA_SERVER")
                .baseUrl("https://new.jira.com")
                .authType("PAT")
                .credentials(Map.of("pat", "my-pat"))
                .metadata(Map.of("version", "8.0"))
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("my-pat")).thenReturn("encrypted-pat");
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(existing);

        PlatformConnection result = connectionService.updateConnection(connectionId, request);

        assertNotNull(result);
        assertEquals("JIRA_SERVER", existing.getPlatformType());
        assertEquals("https://new.jira.com", existing.getBaseUrl());
        verify(connectionRepository).save(existing);
    }

    @Test
    void should_updateConnection_when_noNewCredentials() {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection existing = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://old.atlassian.net")
                .authType("OAUTH2")
                .credentials("{\"clientId\":\"old\"}")
                .build();

        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(existing.getTenantId())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://new.atlassian.net")
                .authType("OAUTH2")
                .credentials(null)
                .metadata(null)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(existing));
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(existing);

        PlatformConnection result = connectionService.updateConnection(connectionId, request);

        assertNotNull(result);
        // Credentials should not be overwritten when null
        assertEquals("{\"clientId\":\"old\"}", existing.getCredentials());
    }

    @Test
    void should_deleteConnection_when_exists() {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        connectionService.deleteConnection(connectionId);

        verify(connectionRepository).delete(connection);
    }

    @Test
    void should_throwNotFound_when_deleteMissingConnection() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionService.deleteConnection(connectionId));
    }

    @Test
    void should_testConnection_when_successful() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.testConnection()).thenReturn(true);
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(connection);

        boolean result = connectionService.testConnection(connectionId);

        assertTrue(result);
        assertEquals("ACTIVE", connection.getStatus());
        verify(adapter).configure("https://example.atlassian.net", "plain-token");
        verify(adapter).testConnection();
    }

    @Test
    void should_setErrorStatus_when_testConnectionFails() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.testConnection()).thenReturn(false);
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(connection);

        boolean result = connectionService.testConnection(connectionId);

        assertFalse(result);
        assertEquals("ERROR", connection.getStatus());
    }

    @Test
    void should_throwPlatformIntegrationException_when_testConnectionThrows() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(Map.of("accessToken", "encrypted-token"));
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .credentials(credJson)
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(adapterRegistry.getAdapter("JIRA_CLOUD")).thenReturn(adapter);
        when(encryptionService.decrypt("encrypted-token")).thenReturn("plain-token");
        when(adapter.testConnection()).thenThrow(new RuntimeException("Network error"));
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(connection);

        assertThrows(PlatformIntegrationException.class,
                () -> connectionService.testConnection(connectionId));
        assertEquals("ERROR", connection.getStatus());
    }

    @Test
    void should_getDecryptedCredentials_when_connectionHasCredentials() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String credJson = objectMapper.writeValueAsString(
                Map.of("clientId", "plain-id", "clientSecret", "encrypted-secret"));
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .credentials(credJson)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("decrypted-secret");

        Map<String, String> result = connectionService.getDecryptedCredentials(connectionId);

        assertEquals("plain-id", result.get("clientId"));
        assertEquals("decrypted-secret", result.get("clientSecret"));
    }

    @Test
    void should_returnEmptyMap_when_noCredentials() {
        UUID connectionId = UUID.randomUUID();
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .credentials(null)
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        Map<String, String> result = connectionService.getDecryptedCredentials(connectionId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_encryptSensitiveFields_when_creatingConnection() {
        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(UUID.randomUUID())
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .credentials(Map.of(
                        "clientId", "plain-id",
                        "clientSecret", "my-secret",
                        "accessToken", "my-token"
                ))
                .build();

        when(encryptionService.encrypt("my-secret")).thenReturn("encrypted-secret");
        when(encryptionService.encrypt("my-token")).thenReturn("encrypted-token");

        PlatformConnection savedConnection = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(request.getTenantId())
                .platformType("JIRA_CLOUD")
                .status("ACTIVE")
                .build();
        when(connectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        connectionService.createConnection(request);

        ArgumentCaptor<PlatformConnection> captor = ArgumentCaptor.forClass(PlatformConnection.class);
        verify(connectionRepository).save(captor.capture());

        String credentialsJson = captor.getValue().getCredentials();
        assertNotNull(credentialsJson);
        assertTrue(credentialsJson.contains("encrypted-secret"));
        assertTrue(credentialsJson.contains("encrypted-token"));
        assertTrue(credentialsJson.contains("plain-id")); // clientId is not sensitive
    }
}
