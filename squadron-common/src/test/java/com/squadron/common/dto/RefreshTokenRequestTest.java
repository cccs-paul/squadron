package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenRequestTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("refresh-token-abc123")
                .build();

        assertEquals("refresh-token-abc123", request.getRefreshToken());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        RefreshTokenRequest request = new RefreshTokenRequest();

        assertNull(request.getRefreshToken());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        RefreshTokenRequest request = new RefreshTokenRequest("my-refresh-token");

        assertEquals("my-refresh-token", request.getRefreshToken());
    }

    @Test
    void should_setAndGetField_when_setterCalled() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("updated-token");

        assertEquals("updated-token", request.getRefreshToken());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        RefreshTokenRequest request1 = RefreshTokenRequest.builder()
                .refreshToken("token")
                .build();

        RefreshTokenRequest request2 = RefreshTokenRequest.builder()
                .refreshToken("token")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        RefreshTokenRequest request1 = RefreshTokenRequest.builder()
                .refreshToken("token-1")
                .build();

        RefreshTokenRequest request2 = RefreshTokenRequest.builder()
                .refreshToken("token-2")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_includeFieldInToString_when_toStringCalled() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("my-token-value")
                .build();

        String str = request.toString();
        assertTrue(str.contains("my-token-value"));
    }

    @Test
    void should_handleNullValue_when_fieldIsNull() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(null)
                .build();

        assertNull(request.getRefreshToken());
    }

    @Test
    void should_handleEmptyString_when_emptyTokenProvided() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("")
                .build();

        assertEquals("", request.getRefreshToken());
    }
}
