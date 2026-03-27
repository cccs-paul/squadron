package com.squadron.platform.dto;

import com.squadron.platform.entity.PlatformConnection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionInfoResponseTest {

    @Test
    void should_buildWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        ConnectionInfoResponse response = ConnectionInfoResponse.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .status("ACTIVE")
                .createdAt(createdAt)
                .build();

        assertEquals(id, response.getId());
        assertEquals(tenantId, response.getTenantId());
        assertEquals("JIRA_CLOUD", response.getPlatformType());
        assertEquals("https://example.atlassian.net", response.getBaseUrl());
        assertEquals("OAUTH2", response.getAuthType());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    void should_supportNoArgsConstructor() {
        ConnectionInfoResponse response = new ConnectionInfoResponse();
        assertNull(response.getId());
        assertNull(response.getTenantId());
        assertNull(response.getPlatformType());
        assertNull(response.getBaseUrl());
        assertNull(response.getAuthType());
        assertNull(response.getStatus());
        assertNull(response.getCreatedAt());
    }

    @Test
    void should_supportSetters() {
        ConnectionInfoResponse response = new ConnectionInfoResponse();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        response.setId(id);
        response.setTenantId(tenantId);
        response.setPlatformType("GITHUB");
        response.setBaseUrl("https://api.github.com");
        response.setAuthType("PAT");
        response.setStatus("ACTIVE");
        response.setCreatedAt(createdAt);

        assertEquals(id, response.getId());
        assertEquals(tenantId, response.getTenantId());
        assertEquals("GITHUB", response.getPlatformType());
        assertEquals("https://api.github.com", response.getBaseUrl());
        assertEquals("PAT", response.getAuthType());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");

        ConnectionInfoResponse r1 = ConnectionInfoResponse.builder()
                .id(id).tenantId(tenantId).platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net").authType("OAUTH2")
                .status("ACTIVE").createdAt(createdAt).build();
        ConnectionInfoResponse r2 = ConnectionInfoResponse.builder()
                .id(id).tenantId(tenantId).platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net").authType("OAUTH2")
                .status("ACTIVE").createdAt(createdAt).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        ConnectionInfoResponse r1 = ConnectionInfoResponse.builder()
                .platformType("JIRA_CLOUD").build();
        ConnectionInfoResponse r2 = ConnectionInfoResponse.builder()
                .platformType("GITHUB").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_implementToString() {
        ConnectionInfoResponse response = ConnectionInfoResponse.builder()
                .platformType("GITLAB")
                .status("ACTIVE")
                .build();
        String str = response.toString();
        assertNotNull(str);
        assertTrue(str.contains("GITLAB"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void should_createFromEntity() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PlatformConnection connection = PlatformConnection.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .status("ACTIVE")
                .credentials("{\"clientSecret\":\"encrypted\"}")
                .webhookSecret("webhook-secret")
                .createdAt(createdAt)
                .build();

        ConnectionInfoResponse response = ConnectionInfoResponse.fromEntity(connection);

        assertEquals(id, response.getId());
        assertEquals(tenantId, response.getTenantId());
        assertEquals("JIRA_CLOUD", response.getPlatformType());
        assertEquals("https://example.atlassian.net", response.getBaseUrl());
        assertEquals("OAUTH2", response.getAuthType());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    void should_omitSensitiveFields_when_createdFromEntity() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("PAT")
                .status("ACTIVE")
                .credentials("{\"pat\":\"super-secret-token\"}")
                .webhookSecret("webhook-secret-value")
                .createdAt(Instant.now())
                .build();

        ConnectionInfoResponse response = ConnectionInfoResponse.fromEntity(connection);
        String str = response.toString();

        // Verify sensitive fields are not in the response DTO
        assertFalse(str.contains("super-secret-token"));
        assertFalse(str.contains("webhook-secret-value"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        ConnectionInfoResponse response = new ConnectionInfoResponse(
                id, tenantId, "AZURE_DEVOPS", "https://dev.azure.com", "PAT", "ACTIVE", createdAt
        );

        assertEquals(id, response.getId());
        assertEquals("AZURE_DEVOPS", response.getPlatformType());
        assertEquals("ACTIVE", response.getStatus());
    }
}
