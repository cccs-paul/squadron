package com.squadron.common.dto;

import com.squadron.common.security.CredentialPurpose;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResolveCredentialRequestTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.GIT_CLONE)
                .build();

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals(CredentialPurpose.GIT_CLONE, request.getPurpose());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ResolveCredentialRequest request = new ResolveCredentialRequest();

        assertNull(request.getUserId());
        assertNull(request.getConnectionId());
        assertNull(request.getPurpose());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request = new ResolveCredentialRequest(
                userId, connectionId, CredentialPurpose.PLATFORM_API
        );

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals(CredentialPurpose.PLATFORM_API, request.getPurpose());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ResolveCredentialRequest request = new ResolveCredentialRequest();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        request.setUserId(userId);
        request.setConnectionId(connectionId);
        request.setPurpose(CredentialPurpose.FULL);

        assertEquals(userId, request.getUserId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals(CredentialPurpose.FULL, request.getPurpose());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request1 = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.GIT_PUSH)
                .build();

        ResolveCredentialRequest request2 = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.GIT_PUSH)
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ResolveCredentialRequest request1 = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .purpose(CredentialPurpose.GIT_CLONE)
                .build();

        ResolveCredentialRequest request2 = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .purpose(CredentialPurpose.PLATFORM_API)
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        UUID userId = UUID.randomUUID();

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .purpose(CredentialPurpose.FULL)
                .build();

        String str = request.toString();
        assertTrue(str.contains(userId.toString()));
        assertTrue(str.contains("FULL"));
    }
}
