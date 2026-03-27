package com.squadron.platform.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPlatformTokenTest {

    @Test
    void should_buildWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(id)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-access")
                .refreshToken("encrypted-refresh")
                .expiresAt(now.plusSeconds(3600))
                .scopes("read write")
                .tokenType("oauth2")
                .tokenMetadata("{\"token_type\":\"Bearer\"}")
                .build();

        assertEquals(id, token.getId());
        assertEquals(userId, token.getUserId());
        assertEquals(connectionId, token.getConnectionId());
        assertEquals("encrypted-access", token.getAccessToken());
        assertEquals("encrypted-refresh", token.getRefreshToken());
        assertEquals(now.plusSeconds(3600), token.getExpiresAt());
        assertEquals("read write", token.getScopes());
        assertEquals("oauth2", token.getTokenType());
        assertEquals("{\"token_type\":\"Bearer\"}", token.getTokenMetadata());
    }

    @Test
    void should_haveDefaultTokenTypeOauth2() {
        UserPlatformToken token = UserPlatformToken.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .accessToken("token")
                .build();

        assertEquals("oauth2", token.getTokenType());
    }

    @Test
    void should_supportNoArgsConstructor() {
        UserPlatformToken token = new UserPlatformToken();
        assertNull(token.getId());
        assertNull(token.getUserId());
    }

    @Test
    void should_supportSetters() {
        UserPlatformToken token = new UserPlatformToken();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        token.setUserId(userId);
        token.setConnectionId(connectionId);
        token.setAccessToken("my-token");
        token.setRefreshToken("my-refresh");
        token.setTokenType("pat");

        assertEquals(userId, token.getUserId());
        assertEquals(connectionId, token.getConnectionId());
        assertEquals("my-token", token.getAccessToken());
        assertEquals("my-refresh", token.getRefreshToken());
        assertEquals("pat", token.getTokenType());
    }

    @Test
    void should_setTimestampsOnCreate() {
        UserPlatformToken token = new UserPlatformToken();
        token.onCreate();

        assertNotNull(token.getCreatedAt());
        assertNotNull(token.getUpdatedAt());
    }

    @Test
    void should_updateTimestampOnUpdate() {
        UserPlatformToken token = new UserPlatformToken();
        token.onCreate();
        Instant createdAt = token.getCreatedAt();

        token.onUpdate();

        assertEquals(createdAt, token.getCreatedAt());
        assertNotNull(token.getUpdatedAt());
    }

    @Test
    void should_allowNullRefreshToken_forPatType() {
        UserPlatformToken token = UserPlatformToken.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .accessToken("encrypted-pat")
                .refreshToken(null)
                .tokenType("pat")
                .build();

        assertNull(token.getRefreshToken());
        assertEquals("pat", token.getTokenType());
    }
}
