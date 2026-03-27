package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2AuthorizeUrlResponseTest {

    @Test
    void should_buildWithAllFields() {
        UUID connectionId = UUID.randomUUID();

        OAuth2AuthorizeUrlResponse response = OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl("https://auth.example.com/authorize?client_id=abc&state=xyz")
                .state("xyz-state-123")
                .connectionId(connectionId)
                .platformType("JIRA_CLOUD")
                .build();

        assertEquals("https://auth.example.com/authorize?client_id=abc&state=xyz", response.getAuthorizeUrl());
        assertEquals("xyz-state-123", response.getState());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("JIRA_CLOUD", response.getPlatformType());
    }

    @Test
    void should_supportNoArgsConstructor() {
        OAuth2AuthorizeUrlResponse response = new OAuth2AuthorizeUrlResponse();
        assertNull(response.getAuthorizeUrl());
        assertNull(response.getState());
        assertNull(response.getConnectionId());
        assertNull(response.getPlatformType());
    }

    @Test
    void should_supportSetters() {
        OAuth2AuthorizeUrlResponse response = new OAuth2AuthorizeUrlResponse();
        UUID connectionId = UUID.randomUUID();

        response.setAuthorizeUrl("https://example.com/auth");
        response.setState("state-val");
        response.setConnectionId(connectionId);
        response.setPlatformType("GITHUB");

        assertEquals("https://example.com/auth", response.getAuthorizeUrl());
        assertEquals("state-val", response.getState());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("GITHUB", response.getPlatformType());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID connectionId = UUID.randomUUID();

        OAuth2AuthorizeUrlResponse r1 = OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl("https://auth.example.com").state("s1")
                .connectionId(connectionId).platformType("JIRA_CLOUD").build();
        OAuth2AuthorizeUrlResponse r2 = OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl("https://auth.example.com").state("s1")
                .connectionId(connectionId).platformType("JIRA_CLOUD").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        OAuth2AuthorizeUrlResponse r1 = OAuth2AuthorizeUrlResponse.builder()
                .state("state-a").build();
        OAuth2AuthorizeUrlResponse r2 = OAuth2AuthorizeUrlResponse.builder()
                .state("state-b").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_implementToString() {
        OAuth2AuthorizeUrlResponse response = OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl("https://auth.example.com/authorize")
                .platformType("GITLAB")
                .build();
        String str = response.toString();
        assertNotNull(str);
        assertTrue(str.contains("https://auth.example.com/authorize"));
        assertTrue(str.contains("GITLAB"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID connectionId = UUID.randomUUID();

        OAuth2AuthorizeUrlResponse response = new OAuth2AuthorizeUrlResponse(
                "https://auth.example.com", "my-state", connectionId, "AZURE_DEVOPS"
        );

        assertEquals("https://auth.example.com", response.getAuthorizeUrl());
        assertEquals("my-state", response.getState());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("AZURE_DEVOPS", response.getPlatformType());
    }
}
