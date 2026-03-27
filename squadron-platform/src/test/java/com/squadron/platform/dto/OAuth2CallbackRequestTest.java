package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2CallbackRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        OAuth2CallbackRequest request = OAuth2CallbackRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("auth-code-xyz")
                .redirectUri("https://app.example.com/callback")
                .state("state-abc")
                .build();

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("auth-code-xyz", request.getAuthorizationCode());
        assertEquals("https://app.example.com/callback", request.getRedirectUri());
        assertEquals("state-abc", request.getState());
    }

    @Test
    void should_supportNoArgsConstructor() {
        OAuth2CallbackRequest request = new OAuth2CallbackRequest();
        assertNull(request.getUserId());
        assertNull(request.getConnectionId());
        assertNull(request.getAuthorizationCode());
        assertNull(request.getRedirectUri());
        assertNull(request.getState());
    }

    @Test
    void should_supportSetters() {
        OAuth2CallbackRequest request = new OAuth2CallbackRequest();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        request.setUserId(userId);
        request.setConnectionId(connectionId);
        request.setAuthorizationCode("code-123");
        request.setRedirectUri("https://example.com/callback");
        request.setState("state-val");

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("code-123", request.getAuthorizationCode());
        assertEquals("https://example.com/callback", request.getRedirectUri());
        assertEquals("state-val", request.getState());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        OAuth2CallbackRequest r1 = OAuth2CallbackRequest.builder()
                .userId(userId).connectionId(connectionId)
                .authorizationCode("code").redirectUri("uri").state("state").build();
        OAuth2CallbackRequest r2 = OAuth2CallbackRequest.builder()
                .userId(userId).connectionId(connectionId)
                .authorizationCode("code").redirectUri("uri").state("state").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        OAuth2CallbackRequest r1 = OAuth2CallbackRequest.builder()
                .authorizationCode("code-1").build();
        OAuth2CallbackRequest r2 = OAuth2CallbackRequest.builder()
                .authorizationCode("code-2").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_implementToString() {
        OAuth2CallbackRequest request = OAuth2CallbackRequest.builder()
                .authorizationCode("my-auth-code")
                .state("my-state")
                .build();
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("my-auth-code"));
        assertTrue(str.contains("my-state"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        OAuth2CallbackRequest request = new OAuth2CallbackRequest(
                userId, connectionId, "code", "https://redirect.uri", "state"
        );

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("code", request.getAuthorizationCode());
        assertEquals("https://redirect.uri", request.getRedirectUri());
        assertEquals("state", request.getState());
    }
}
