package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateConnectionRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID tenantId = UUID.randomUUID();
        Map<String, String> creds = Map.of("clientId", "id123", "clientSecret", "secret");
        Map<String, Object> metadata = Map.of("env", "prod");

        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .credentials(creds)
                .metadata(metadata)
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals("JIRA_CLOUD", request.getPlatformType());
        assertEquals("https://example.atlassian.net", request.getBaseUrl());
        assertEquals("OAUTH2", request.getAuthType());
        assertEquals(creds, request.getCredentials());
        assertEquals(metadata, request.getMetadata());
    }

    @Test
    void should_supportNoArgsConstructor() {
        CreateConnectionRequest request = new CreateConnectionRequest();
        assertNull(request.getTenantId());
        assertNull(request.getPlatformType());
    }

    @Test
    void should_supportSetters() {
        CreateConnectionRequest request = new CreateConnectionRequest();
        UUID tenantId = UUID.randomUUID();
        request.setTenantId(tenantId);
        request.setPlatformType("GITHUB");
        request.setBaseUrl("https://api.github.com");
        request.setAuthType("PAT");

        assertEquals(tenantId, request.getTenantId());
        assertEquals("GITHUB", request.getPlatformType());
        assertEquals("https://api.github.com", request.getBaseUrl());
        assertEquals("PAT", request.getAuthType());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        CreateConnectionRequest r1 = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .build();
        CreateConnectionRequest r2 = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("OAUTH2")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        CreateConnectionRequest r1 = CreateConnectionRequest.builder()
                .platformType("JIRA_CLOUD")
                .build();
        CreateConnectionRequest r2 = CreateConnectionRequest.builder()
                .platformType("GITHUB")
                .build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_implementToString() {
        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .platformType("JIRA_CLOUD")
                .build();
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("JIRA_CLOUD"));
    }
}
