package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CodeGenerationRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CodeGenerationRequest request = CodeGenerationRequest.builder()
                .workspaceId(workspaceId)
                .taskId(taskId)
                .tenantId(tenantId)
                .projectId(projectId)
                .taskTitle("Implement login feature")
                .platform("github")
                .repoOwner("squadron")
                .repoName("backend")
                .accessToken("ghp_abc123")
                .commitMessage("feat: add login endpoint")
                .prTitle("Add login feature")
                .prDescription("Implements JWT-based login")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("Implement login feature", request.getTaskTitle());
        assertEquals("github", request.getPlatform());
        assertEquals("squadron", request.getRepoOwner());
        assertEquals("backend", request.getRepoName());
        assertEquals("ghp_abc123", request.getAccessToken());
        assertEquals("feat: add login endpoint", request.getCommitMessage());
        assertEquals("Add login feature", request.getPrTitle());
        assertEquals("Implements JWT-based login", request.getPrDescription());
    }

    @Test
    void should_createRequest_when_usingNoArgsConstructor() {
        CodeGenerationRequest request = new CodeGenerationRequest();

        assertNull(request.getWorkspaceId());
        assertNull(request.getTaskId());
        assertNull(request.getTenantId());
        assertNull(request.getProjectId());
        assertNull(request.getTaskTitle());
        assertNull(request.getPlatform());
        assertNull(request.getRepoOwner());
        assertNull(request.getRepoName());
        assertNull(request.getAccessToken());
        assertNull(request.getCommitMessage());
        assertNull(request.getPrTitle());
        assertNull(request.getPrDescription());
    }

    @Test
    void should_createRequest_when_usingAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CodeGenerationRequest request = new CodeGenerationRequest(
                workspaceId, taskId, tenantId, projectId,
                "Fix bug", "gitlab", "org", "repo",
                "token123", "fix: null check", "Fix NPE", "Adds null check"
        );

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("Fix bug", request.getTaskTitle());
        assertEquals("gitlab", request.getPlatform());
        assertEquals("org", request.getRepoOwner());
        assertEquals("repo", request.getRepoName());
        assertEquals("token123", request.getAccessToken());
        assertEquals("fix: null check", request.getCommitMessage());
        assertEquals("Fix NPE", request.getPrTitle());
        assertEquals("Adds null check", request.getPrDescription());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        CodeGenerationRequest request = new CodeGenerationRequest();
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        request.setWorkspaceId(workspaceId);
        request.setTaskId(taskId);
        request.setTenantId(tenantId);
        request.setProjectId(projectId);
        request.setTaskTitle("Refactor service");
        request.setPlatform("bitbucket");
        request.setRepoOwner("myorg");
        request.setRepoName("myrepo");
        request.setAccessToken("bb_token");
        request.setCommitMessage("refactor: extract method");
        request.setPrTitle("Refactor service layer");
        request.setPrDescription("Extracts common logic");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("Refactor service", request.getTaskTitle());
        assertEquals("bitbucket", request.getPlatform());
        assertEquals("myorg", request.getRepoOwner());
        assertEquals("myrepo", request.getRepoName());
        assertEquals("bb_token", request.getAccessToken());
        assertEquals("refactor: extract method", request.getCommitMessage());
        assertEquals("Refactor service layer", request.getPrTitle());
        assertEquals("Extracts common logic", request.getPrDescription());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CodeGenerationRequest r1 = CodeGenerationRequest.builder()
                .workspaceId(workspaceId).taskId(taskId).tenantId(tenantId).projectId(projectId)
                .taskTitle("task").platform("github").repoOwner("owner").repoName("repo")
                .accessToken("token").commitMessage("msg").prTitle("title").prDescription("desc")
                .build();
        CodeGenerationRequest r2 = CodeGenerationRequest.builder()
                .workspaceId(workspaceId).taskId(taskId).tenantId(tenantId).projectId(projectId)
                .taskTitle("task").platform("github").repoOwner("owner").repoName("repo")
                .accessToken("token").commitMessage("msg").prTitle("title").prDescription("desc")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        CodeGenerationRequest r1 = CodeGenerationRequest.builder()
                .platform("github").repoOwner("owner1").build();
        CodeGenerationRequest r2 = CodeGenerationRequest.builder()
                .platform("github").repoOwner("owner2").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        CodeGenerationRequest request = CodeGenerationRequest.builder()
                .platform("github").repoName("backend").build();
        String toString = request.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("github"));
        assertTrue(toString.contains("backend"));
    }
}
