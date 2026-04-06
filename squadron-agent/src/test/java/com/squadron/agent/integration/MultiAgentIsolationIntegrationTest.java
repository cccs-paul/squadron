package com.squadron.agent.integration;

import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.service.WorkspaceLifecycleService;
import com.squadron.agent.tool.builtin.CredentialClient;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.TaskContext;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: verifies that two agents working on different tasks for the
 * same repo each get their own workspace, branch, and credentials with no
 * cross-contamination.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Agent Isolation Integration")
class MultiAgentIsolationIntegrationTest {

    @Mock private CredentialClient credentialClient;
    @Mock private WorkspaceClient workspaceClient;
    @Mock private GitClient gitClient;

    private WorkspaceLifecycleService lifecycleService;

    private UUID tenantId, projectId, connectionId;
    private UUID userId1, userId2;
    private UUID taskId1, taskId2;
    private UUID workspaceId1, workspaceId2;

    @BeforeEach
    void setUp() {
        lifecycleService = new WorkspaceLifecycleService(credentialClient, workspaceClient, gitClient, true);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        taskId1 = UUID.randomUUID();
        taskId2 = UUID.randomUUID();
        workspaceId1 = UUID.randomUUID();
        workspaceId2 = UUID.randomUUID();
    }

    private TaskContext buildContext(UUID taskId, UUID userId, String title, String externalId) {
        return TaskContext.builder()
                .taskId(taskId).tenantId(tenantId).projectId(projectId)
                .userId(userId).connectionId(connectionId)
                .repoUrl("https://github.com/acme/shared-repo.git")
                .defaultBranch("main")
                .branchStrategy("GITHUB_FLOW")
                .branchNamingTemplate("{strategy}/{ticket}-{description}")
                .externalId(externalId).title(title)
                .build();
    }

    @Test
    @DisplayName("should create separate workspaces for two agents on different tasks")
    void should_createSeparateWorkspaces_when_twoAgentsOnSameRepo() {
        // Agent 1 credentials
        var creds1 = CredentialResolutionResult.builder()
                .accessToken("ghp_user1_token").credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN).build();
        // Agent 2 credentials
        var creds2 = CredentialResolutionResult.builder()
                .accessToken("ghp_user2_token").credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN).build();

        when(credentialClient.resolveCredentials(eq(userId1), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds1);
        when(credentialClient.resolveCredentials(eq(userId2), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds2);

        // Workspace 1
        Map<String, Object> ws1 = new HashMap<>();
        ws1.put("id", workspaceId1.toString());
        ws1.put("status", "READY");
        // Workspace 2
        Map<String, Object> ws2 = new HashMap<>();
        ws2.put("id", workspaceId2.toString());
        ws2.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(ws1, ws2);

        when(gitClient.generateBranchName(eq(tenantId), eq(projectId), eq(taskId1), anyString()))
                .thenReturn("feature/SQ-10/add-auth");
        when(gitClient.generateBranchName(eq(tenantId), eq(projectId), eq(taskId2), anyString()))
                .thenReturn("feature/SQ-11/fix-logout");

        // Provision for agent 1
        TaskContext ctx1 = buildContext(taskId1, userId1, "Add auth", "SQ-10");
        WorkspaceInfo info1 = lifecycleService.provisionWorkspace(ctx1);

        // Provision for agent 2
        TaskContext ctx2 = buildContext(taskId2, userId2, "Fix logout", "SQ-11");
        WorkspaceInfo info2 = lifecycleService.provisionWorkspace(ctx2);

        // Verify isolation
        assertNotEquals(info1.getWorkspaceId(), info2.getWorkspaceId());
        assertNotEquals(info1.getBranchName(), info2.getBranchName());
        assertEquals("feature/SQ-10/add-auth", info1.getBranchName());
        assertEquals("feature/SQ-11/fix-logout", info2.getBranchName());

        // Verify each got own credential resolution
        verify(credentialClient).resolveCredentials(userId1, connectionId, CredentialPurpose.GIT_CLONE);
        verify(credentialClient).resolveCredentials(userId2, connectionId, CredentialPurpose.GIT_CLONE);

        // Verify two separate workspace creations
        verify(workspaceClient, times(2)).createWorkspace(anyMap());
    }

    @Test
    @DisplayName("should generate unique branch names for different tasks on same repo")
    void should_generateUniqueBranches_when_differentTasksOnSameRepo() {
        var creds = CredentialResolutionResult.builder()
                .accessToken("ghp_token").credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN).build();

        when(credentialClient.resolveCredentials(any(), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> ws = new HashMap<>();
        ws.put("id", workspaceId1.toString());
        ws.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(ws);

        when(gitClient.generateBranchName(eq(tenantId), eq(projectId), eq(taskId1), anyString()))
                .thenReturn("feature/SQ-10/task-one");
        when(gitClient.generateBranchName(eq(tenantId), eq(projectId), eq(taskId2), anyString()))
                .thenReturn("feature/SQ-11/task-two");

        TaskContext ctx1 = buildContext(taskId1, userId1, "Task one", "SQ-10");
        WorkspaceInfo info1 = lifecycleService.provisionWorkspace(ctx1);

        TaskContext ctx2 = buildContext(taskId2, userId1, "Task two", "SQ-11");
        WorkspaceInfo info2 = lifecycleService.provisionWorkspace(ctx2);

        assertNotEquals(info1.getBranchName(), info2.getBranchName());
        assertEquals("feature/SQ-10/task-one", info1.getBranchName());
        assertEquals("feature/SQ-11/task-two", info2.getBranchName());
    }
}
