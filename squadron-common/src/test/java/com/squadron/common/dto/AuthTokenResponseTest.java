package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthTokenResponseTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UserDto user = UserDto.builder().email("test@example.com").build();

        AuthTokenResponse response = AuthTokenResponse.builder()
                .accessToken("access-123")
                .refreshToken("refresh-456")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(user)
                .build();

        assertEquals("access-123", response.getAccessToken());
        assertEquals("refresh-456", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(user, response.getUser());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        AuthTokenResponse response = new AuthTokenResponse();

        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getTokenType());
        assertEquals(0L, response.getExpiresIn());
        assertNull(response.getUser());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UserDto user = UserDto.builder().email("user@test.com").build();

        AuthTokenResponse response = new AuthTokenResponse(
                "access-token", "refresh-token", "Bearer", 7200L, user
        );

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals(user, response.getUser());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        AuthTokenResponse response = new AuthTokenResponse();
        response.setAccessToken("new-access");
        response.setRefreshToken("new-refresh");
        response.setTokenType("MAC");
        response.setExpiresIn(1800L);

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
        assertEquals("MAC", response.getTokenType());
        assertEquals(1800L, response.getExpiresIn());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UserDto user = UserDto.builder().email("test@test.com").build();

        AuthTokenResponse response1 = AuthTokenResponse.builder()
                .accessToken("token")
                .refreshToken("refresh")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(user)
                .build();

        AuthTokenResponse response2 = AuthTokenResponse.builder()
                .accessToken("token")
                .refreshToken("refresh")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(user)
                .build();

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        AuthTokenResponse response1 = AuthTokenResponse.builder()
                .accessToken("token-1")
                .build();

        AuthTokenResponse response2 = AuthTokenResponse.builder()
                .accessToken("token-2")
                .build();

        assertNotEquals(response1, response2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        AuthTokenResponse response = AuthTokenResponse.builder()
                .accessToken("abc")
                .tokenType("Bearer")
                .build();

        String str = response.toString();
        assertTrue(str.contains("abc"));
        assertTrue(str.contains("Bearer"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        AuthTokenResponse response = AuthTokenResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .tokenType(null)
                .user(null)
                .build();

        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getTokenType());
        assertNull(response.getUser());
    }

    @Test
    void should_defaultExpiresInToZero_when_notSet() {
        AuthTokenResponse response = AuthTokenResponse.builder().build();

        assertEquals(0L, response.getExpiresIn());
    }
}
