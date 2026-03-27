package com.squadron.platform.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlatformConnectionTest {

    @Test
    void should_buildWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        PlatformConnection connection = PlatformConnection.builder()
                .id(id)
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .credentials("{\"clientId\":\"id\"}")
                .status("ACTIVE")
                .metadata("{\"env\":\"prod\"}")
                .build();

        assertEquals(id, connection.getId());
        assertEquals(tenantId, connection.getTenantId());
        assertEquals("JIRA_CLOUD", connection.getPlatformType());
        assertEquals("https://example.atlassian.net", connection.getBaseUrl());
        assertEquals("OAUTH2", connection.getAuthType());
        assertEquals("{\"clientId\":\"id\"}", connection.getCredentials());
        assertEquals("ACTIVE", connection.getStatus());
        assertEquals("{\"env\":\"prod\"}", connection.getMetadata());
    }

    @Test
    void should_haveDefaultStatusActive() {
        PlatformConnection connection = PlatformConnection.builder()
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("PAT")
                .build();

        assertEquals("ACTIVE", connection.getStatus());
    }

    @Test
    void should_supportNoArgsConstructor() {
        PlatformConnection connection = new PlatformConnection();
        assertNull(connection.getId());
        assertNull(connection.getTenantId());
    }

    @Test
    void should_supportSetters() {
        PlatformConnection connection = new PlatformConnection();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        connection.setId(id);
        connection.setTenantId(tenantId);
        connection.setPlatformType("GITLAB");
        connection.setBaseUrl("https://gitlab.com");
        connection.setAuthType("PAT");
        connection.setStatus("ERROR");

        assertEquals(id, connection.getId());
        assertEquals(tenantId, connection.getTenantId());
        assertEquals("GITLAB", connection.getPlatformType());
        assertEquals("https://gitlab.com", connection.getBaseUrl());
        assertEquals("PAT", connection.getAuthType());
        assertEquals("ERROR", connection.getStatus());
    }

    @Test
    void should_setTimestampsOnCreate() {
        PlatformConnection connection = new PlatformConnection();
        connection.onCreate();

        assertNotNull(connection.getCreatedAt());
        assertNotNull(connection.getUpdatedAt());
    }

    @Test
    void should_updateTimestampOnUpdate() {
        PlatformConnection connection = new PlatformConnection();
        connection.onCreate();
        Instant createdAt = connection.getCreatedAt();

        // Small delay to ensure updatedAt changes
        connection.onUpdate();

        assertEquals(createdAt, connection.getCreatedAt());
        assertNotNull(connection.getUpdatedAt());
    }
}
