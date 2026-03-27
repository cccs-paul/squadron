package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PushRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("upstream")
                .branch("main")
                .accessToken("ghp_token")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("upstream", request.getRemoteName());
        assertEquals("main", request.getBranch());
        assertEquals("ghp_token", request.getAccessToken());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        PushRequest request = new PushRequest();
        assertNull(request.getWorkspaceId());
        // Field initializer "origin" runs for no-args constructor too
        assertEquals("origin", request.getRemoteName());
        assertNull(request.getBranch());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = new PushRequest(workspaceId, "origin", "develop", "token");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("origin", request.getRemoteName());
        assertEquals("develop", request.getBranch());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_useDefaultRemoteName_when_notExplicitlySet() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .branch("main")
                .accessToken("token")
                .build();

        assertEquals("origin", request.getRemoteName());
    }

    @Test
    void should_supportSettersAndGetters() {
        PushRequest request = new PushRequest();
        UUID workspaceId = UUID.randomUUID();
        request.setWorkspaceId(workspaceId);
        request.setRemoteName("upstream");
        request.setBranch("feature/test");
        request.setAccessToken("secret");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("upstream", request.getRemoteName());
        assertEquals("feature/test", request.getBranch());
        assertEquals("secret", request.getAccessToken());
    }

    @Test
    void should_allowNullOptionalFields() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("origin", request.getRemoteName());
        assertNull(request.getBranch());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request1 = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("origin")
                .branch("main")
                .accessToken("token")
                .build();
        PushRequest request2 = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("origin")
                .branch("main")
                .accessToken("token")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentRemoteName() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request1 = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("origin")
                .build();
        PushRequest request2 = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("upstream")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .remoteName("origin")
                .branch("main")
                .accessToken("token")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("workspaceId"));
        assertTrue(str.contains("remoteName"));
        assertTrue(str.contains("branch"));
        assertTrue(str.contains("accessToken"));
    }

    @Test
    void should_overrideDefaultRemoteName_when_explicitlySet() {
        PushRequest request = PushRequest.builder()
                .workspaceId(UUID.randomUUID())
                .remoteName("custom-remote")
                .build();

        assertEquals("custom-remote", request.getRemoteName());
    }
}
