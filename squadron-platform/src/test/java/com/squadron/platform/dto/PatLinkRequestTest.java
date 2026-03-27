package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatLinkRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        PatLinkRequest request = PatLinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("ghp_abc123")
                .build();

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("ghp_abc123", request.getAccessToken());
    }

    @Test
    void should_supportNoArgsConstructor() {
        PatLinkRequest request = new PatLinkRequest();
        assertNull(request.getUserId());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_supportSetters() {
        PatLinkRequest request = new PatLinkRequest();
        UUID userId = UUID.randomUUID();
        request.setUserId(userId);
        request.setAccessToken("my-pat");
        assertEquals(userId, request.getUserId());
        assertEquals("my-pat", request.getAccessToken());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        PatLinkRequest r1 = PatLinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("pat")
                .build();
        PatLinkRequest r2 = PatLinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("pat")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_implementToString() {
        PatLinkRequest request = PatLinkRequest.builder()
                .accessToken("token")
                .build();
        assertNotNull(request.toString());
        assertTrue(request.toString().contains("token"));
    }
}
