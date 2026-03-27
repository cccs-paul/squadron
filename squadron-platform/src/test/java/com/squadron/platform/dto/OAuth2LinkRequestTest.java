package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2LinkRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("auth-code-456")
                .redirectUri("https://app.example.com/oauth/callback")
                .build();

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("auth-code-456", request.getAuthorizationCode());
        assertEquals("https://app.example.com/oauth/callback", request.getRedirectUri());
    }

    @Test
    void should_supportNoArgsConstructor() {
        OAuth2LinkRequest request = new OAuth2LinkRequest();
        assertNull(request.getUserId());
        assertNull(request.getConnectionId());
    }

    @Test
    void should_supportSetters() {
        OAuth2LinkRequest request = new OAuth2LinkRequest();
        UUID userId = UUID.randomUUID();
        request.setUserId(userId);
        request.setAuthorizationCode("code");
        assertEquals(userId, request.getUserId());
        assertEquals("code", request.getAuthorizationCode());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        OAuth2LinkRequest r1 = OAuth2LinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("code")
                .redirectUri("uri")
                .build();
        OAuth2LinkRequest r2 = OAuth2LinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("code")
                .redirectUri("uri")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_implementToString() {
        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .authorizationCode("mycode")
                .build();
        assertNotNull(request.toString());
        assertTrue(request.toString().contains("mycode"));
    }
}
