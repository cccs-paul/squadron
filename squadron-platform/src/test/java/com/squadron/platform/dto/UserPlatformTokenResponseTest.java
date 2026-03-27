package com.squadron.platform.dto;

import com.squadron.platform.entity.UserPlatformToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPlatformTokenResponseTest {

    @Test
    void should_buildWithAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        UserPlatformTokenResponse response = UserPlatformTokenResponse.builder()
                .id(id)
                .userId(userId)
                .connectionId(connectionId)
                .tokenType("oauth2")
                .scopes("read write")
                .expiresAt(now.plusSeconds(3600))
                .hasRefreshToken(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("oauth2", response.getTokenType());
        assertEquals("read write", response.getScopes());
        assertNotNull(response.getExpiresAt());
        assertTrue(response.isHasRefreshToken());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }

    @Test
    void should_createFromEntity_withRefreshToken() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(id)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-access-token")
                .refreshToken("encrypted-refresh-token")
                .tokenType("oauth2")
                .scopes("read write")
                .expiresAt(now.plusSeconds(3600))
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserPlatformTokenResponse response = UserPlatformTokenResponse.fromEntity(token);

        assertEquals(id, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("oauth2", response.getTokenType());
        assertEquals("read write", response.getScopes());
        assertTrue(response.isHasRefreshToken());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }

    @Test
    void should_createFromEntity_withoutRefreshToken() {
        UserPlatformToken token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .accessToken("encrypted-pat")
                .refreshToken(null)
                .tokenType("pat")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UserPlatformTokenResponse response = UserPlatformTokenResponse.fromEntity(token);

        assertEquals("pat", response.getTokenType());
        assertFalse(response.isHasRefreshToken());
    }

    @Test
    void should_supportNoArgsConstructor() {
        UserPlatformTokenResponse response = new UserPlatformTokenResponse();
        assertNull(response.getId());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UserPlatformTokenResponse r1 = UserPlatformTokenResponse.builder()
                .id(id)
                .tokenType("oauth2")
                .createdAt(now)
                .updatedAt(now)
                .build();
        UserPlatformTokenResponse r2 = UserPlatformTokenResponse.builder()
                .id(id)
                .tokenType("oauth2")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_implementToString() {
        UserPlatformTokenResponse response = UserPlatformTokenResponse.builder()
                .tokenType("oauth2")
                .build();
        assertNotNull(response.toString());
        assertTrue(response.toString().contains("oauth2"));
    }
}
