package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlatformLinkRequestTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID connectionId = UUID.randomUUID();

        PlatformLinkRequest request = PlatformLinkRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("auth-code-123")
                .redirectUri("https://app.example.com/oauth/callback")
                .accessToken("pat-token-456")
                .tokenType("oauth2")
                .build();

        assertEquals(connectionId, request.getConnectionId());
        assertEquals("auth-code-123", request.getAuthorizationCode());
        assertEquals("https://app.example.com/oauth/callback", request.getRedirectUri());
        assertEquals("pat-token-456", request.getAccessToken());
        assertEquals("oauth2", request.getTokenType());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        PlatformLinkRequest request = new PlatformLinkRequest();

        assertNull(request.getConnectionId());
        assertNull(request.getAuthorizationCode());
        assertNull(request.getRedirectUri());
        assertNull(request.getAccessToken());
        assertNull(request.getTokenType());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID connectionId = UUID.randomUUID();

        PlatformLinkRequest request = new PlatformLinkRequest(
                connectionId, "code-abc", "https://redirect.uri", "token-xyz", "pat"
        );

        assertEquals(connectionId, request.getConnectionId());
        assertEquals("code-abc", request.getAuthorizationCode());
        assertEquals("https://redirect.uri", request.getRedirectUri());
        assertEquals("token-xyz", request.getAccessToken());
        assertEquals("pat", request.getTokenType());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        PlatformLinkRequest request = new PlatformLinkRequest();
        UUID connectionId = UUID.randomUUID();
        request.setConnectionId(connectionId);
        request.setAuthorizationCode("new-code");
        request.setRedirectUri("https://new-redirect.com");
        request.setAccessToken("new-token");
        request.setTokenType("pat");

        assertEquals(connectionId, request.getConnectionId());
        assertEquals("new-code", request.getAuthorizationCode());
        assertEquals("https://new-redirect.com", request.getRedirectUri());
        assertEquals("new-token", request.getAccessToken());
        assertEquals("pat", request.getTokenType());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID connectionId = UUID.randomUUID();

        PlatformLinkRequest request1 = PlatformLinkRequest.builder()
                .connectionId(connectionId)
                .tokenType("oauth2")
                .build();

        PlatformLinkRequest request2 = PlatformLinkRequest.builder()
                .connectionId(connectionId)
                .tokenType("oauth2")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        PlatformLinkRequest request1 = PlatformLinkRequest.builder()
                .connectionId(UUID.randomUUID())
                .tokenType("oauth2")
                .build();

        PlatformLinkRequest request2 = PlatformLinkRequest.builder()
                .connectionId(UUID.randomUUID())
                .tokenType("pat")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        PlatformLinkRequest request = PlatformLinkRequest.builder()
                .authorizationCode("my-code")
                .tokenType("oauth2")
                .build();

        String str = request.toString();
        assertTrue(str.contains("my-code"));
        assertTrue(str.contains("oauth2"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        PlatformLinkRequest request = PlatformLinkRequest.builder()
                .connectionId(null)
                .authorizationCode(null)
                .redirectUri(null)
                .accessToken(null)
                .tokenType(null)
                .build();

        assertNull(request.getConnectionId());
        assertNull(request.getAuthorizationCode());
        assertNull(request.getRedirectUri());
        assertNull(request.getAccessToken());
        assertNull(request.getTokenType());
    }

    @Test
    void should_supportOAuth2Flow_when_oauthFieldsProvided() {
        UUID connectionId = UUID.randomUUID();
        PlatformLinkRequest request = PlatformLinkRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("oauth-code")
                .redirectUri("https://app.com/callback")
                .tokenType("oauth2")
                .build();

        assertEquals("oauth2", request.getTokenType());
        assertNotNull(request.getAuthorizationCode());
        assertNotNull(request.getRedirectUri());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_supportPatFlow_when_patFieldsProvided() {
        UUID connectionId = UUID.randomUUID();
        PlatformLinkRequest request = PlatformLinkRequest.builder()
                .connectionId(connectionId)
                .accessToken("ghp_abc123")
                .tokenType("pat")
                .build();

        assertEquals("pat", request.getTokenType());
        assertNotNull(request.getAccessToken());
        assertNull(request.getAuthorizationCode());
        assertNull(request.getRedirectUri());
    }
}
