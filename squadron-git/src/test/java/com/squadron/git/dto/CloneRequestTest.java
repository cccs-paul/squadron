package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CloneRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("ghp_token123")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("https://github.com/owner/repo.git", request.getRepoUrl());
        assertEquals("main", request.getBranch());
        assertEquals("ghp_token123", request.getAccessToken());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CloneRequest request = new CloneRequest();
        assertNull(request.getWorkspaceId());
        assertNull(request.getRepoUrl());
        assertNull(request.getBranch());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = new CloneRequest(workspaceId, "https://gitlab.com/repo.git", "develop", "token");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("https://gitlab.com/repo.git", request.getRepoUrl());
        assertEquals("develop", request.getBranch());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_supportSettersAndGetters() {
        CloneRequest request = new CloneRequest();
        UUID workspaceId = UUID.randomUUID();
        request.setWorkspaceId(workspaceId);
        request.setRepoUrl("https://bitbucket.org/repo.git");
        request.setBranch("feature/test");
        request.setAccessToken("secret");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("https://bitbucket.org/repo.git", request.getRepoUrl());
        assertEquals("feature/test", request.getBranch());
        assertEquals("secret", request.getAccessToken());
    }

    @Test
    void should_allowNullOptionalFields() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("https://github.com/owner/repo.git", request.getRepoUrl());
        assertNull(request.getBranch());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request1 = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("token")
                .build();
        CloneRequest request2 = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("token")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentRepoUrl() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request1 = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo1.git")
                .build();
        CloneRequest request2 = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo2.git")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("token")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("workspaceId"));
        assertTrue(str.contains("repoUrl"));
        assertTrue(str.contains("branch"));
        assertTrue(str.contains("accessToken"));
    }
}
