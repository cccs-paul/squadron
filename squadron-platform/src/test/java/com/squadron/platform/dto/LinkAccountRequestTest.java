package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LinkAccountRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID connectionId = UUID.randomUUID();

        LinkAccountRequest request = LinkAccountRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("auth-code-123")
                .redirectUri("https://app.example.com/callback")
                .build();

        assertEquals(connectionId, request.getConnectionId());
        assertEquals("auth-code-123", request.getAuthorizationCode());
        assertEquals("https://app.example.com/callback", request.getRedirectUri());
    }

    @Test
    void should_supportNoArgsConstructor() {
        LinkAccountRequest request = new LinkAccountRequest();
        assertNull(request.getConnectionId());
        assertNull(request.getAuthorizationCode());
    }

    @Test
    void should_supportSetters() {
        LinkAccountRequest request = new LinkAccountRequest();
        UUID connectionId = UUID.randomUUID();
        request.setConnectionId(connectionId);
        request.setAuthorizationCode("code");
        request.setRedirectUri("https://redirect.example.com");

        assertEquals(connectionId, request.getConnectionId());
        assertEquals("code", request.getAuthorizationCode());
        assertEquals("https://redirect.example.com", request.getRedirectUri());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID connectionId = UUID.randomUUID();
        LinkAccountRequest r1 = LinkAccountRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("code")
                .redirectUri("uri")
                .build();
        LinkAccountRequest r2 = LinkAccountRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("code")
                .redirectUri("uri")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_implementToString() {
        LinkAccountRequest request = LinkAccountRequest.builder()
                .authorizationCode("code123")
                .build();
        assertNotNull(request.toString());
        assertTrue(request.toString().contains("code123"));
    }
}
