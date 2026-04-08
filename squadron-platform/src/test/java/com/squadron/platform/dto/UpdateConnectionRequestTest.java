package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdateConnectionRequestTest {

    @Test
    void should_buildWithAllFields() {
        UpdateConnectionRequest request = UpdateConnectionRequest.builder()
                .name("My Connection")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.atlassian.net")
                .authType("PAT")
                .credentials(Map.of("pat", "token123"))
                .metadata(Map.of("key", "value"))
                .build();

        assertEquals("My Connection", request.getName());
        assertEquals("JIRA_CLOUD", request.getPlatformType());
        assertEquals("https://example.atlassian.net", request.getBaseUrl());
        assertEquals("PAT", request.getAuthType());
        assertEquals("token123", request.getCredentials().get("pat"));
        assertEquals("value", request.getMetadata().get("key"));
    }

    @Test
    void should_allowAllFieldsNull() {
        UpdateConnectionRequest request = UpdateConnectionRequest.builder().build();

        assertNull(request.getName());
        assertNull(request.getPlatformType());
        assertNull(request.getBaseUrl());
        assertNull(request.getAuthType());
        assertNull(request.getCredentials());
        assertNull(request.getMetadata());
    }

    @Test
    void should_allowPartialFields() {
        UpdateConnectionRequest request = UpdateConnectionRequest.builder()
                .name("Updated Name")
                .credentials(Map.of("pat", "new-token"))
                .build();

        assertEquals("Updated Name", request.getName());
        assertNull(request.getPlatformType());
        assertNull(request.getBaseUrl());
        assertNull(request.getAuthType());
        assertNotNull(request.getCredentials());
        assertNull(request.getMetadata());
    }

    @Test
    void should_supportSetters() {
        UpdateConnectionRequest request = new UpdateConnectionRequest();
        request.setName("Test");
        request.setPlatformType("GITHUB");

        assertEquals("Test", request.getName());
        assertEquals("GITHUB", request.getPlatformType());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UpdateConnectionRequest r1 = UpdateConnectionRequest.builder()
                .name("Test")
                .platformType("JIRA_CLOUD")
                .build();
        UpdateConnectionRequest r2 = UpdateConnectionRequest.builder()
                .name("Test")
                .platformType("JIRA_CLOUD")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_implementToString() {
        UpdateConnectionRequest request = UpdateConnectionRequest.builder()
                .name("Test")
                .build();

        String toString = request.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Test"));
    }
}
