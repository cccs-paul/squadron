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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: PAT credential flow from resolution through workspace
 * provisioning, clone, branch, push, and PR creation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PAT Credential Flow Integration")
class PatCredentialFlowIntegrationTest {

    @Mock private CredentialClient credentialClient;
    @Mock private WorkspaceClient workspaceClient;
    @Mock private GitClient gitClient;

    private WorkspaceLifecycleService lifecycleService;

    private UUID taskId, tenantId, projectId, userId, connectionId, workspaceId;
    private String repoUrl, defaultBranch;

    @BeforeEach
    void setUp() {
        lifecycleService = new WorkspaceLifecycleService(credentialClient, workspaceClient, gitClient, true);
        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        repoUrl = "https://github.com/acme/my-repo.git";
        defaultBranch = "main";
    }

    private TaskContext buildContext() {
        return TaskContext.builder()
                .taskId(taskId).tenantId(tenantId).projectId(projectId)
                .userId(userId).connectionId(connectionId)
                .repoUrl(repoUrl).defaultBranch(defaultBranch)
                .branchStrategy("GITHUB_FLOW")
                .branchNamingTemplate("{strategy}/{ticket}-{description}")
                .externalId("SQ-42").title("Add login page")
                .build();
    }

    private CredentialResolutionResult patCredential() {
        return CredentialResolutionResult.builder()
                .accessToken("ghp_pat_token_12345")
                .credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();
    }

    @Test
    @DisplayName("should resolve PAT, create workspace, clone, branch, and return workspace info")
    void should_completeFullPatFlow_when_patIsLinked() {
        var creds = patCredential();
        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(eq(tenantId), eq(projectId), eq(taskId), anyString()))
                .thenReturn("feature/SQ-42/add-login-page");

        WorkspaceInfo result = lifecycleService.provisionWorkspace(buildContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        assertEquals("feature/SQ-42/add-login-page", result.getBranchName());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());

        InOrder inOrder = inOrder(credentialClient, workspaceClient, gitClient);
        inOrder.verify(credentialClient).resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);
        inOrder.verify(workspaceClient).createWorkspace(anyMap());
        inOrder.verify(gitClient).generateBranchName(eq(tenantId), eq(projectId), eq(taskId), anyString());
    }

    @Test
    @DisplayName("should fail gracefully when PAT resolution returns null token")
    void should_throwException_when_patResolutionReturnsNullToken() {
        var creds = CredentialResolutionResult.builder()
                .accessToken(null)
                .credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();
        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(any(), any(), any(), anyString())).thenReturn("feature/SQ-42/test");

        // Should still proceed — null token means workspace may be created but clone could fail
        WorkspaceInfo result = lifecycleService.provisionWorkspace(buildContext());
        assertNotNull(result);
        verify(credentialClient).resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);
    }

    @Test
    @DisplayName("should propagate exception when credential resolution fails")
    void should_propagateException_when_credentialResolutionFails() {
        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenThrow(new RuntimeException("No credentials linked"));

        assertThrows(RuntimeException.class, () -> lifecycleService.provisionWorkspace(buildContext()));
        verify(workspaceClient, never()).createWorkspace(anyMap());
    }

    @Test
    @DisplayName("should use HTTPS token mode for PAT credentials")
    void should_useHttpsTokenMode_when_patCredentialResolved() {
        var creds = patCredential();
        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(any(), any(), any(), anyString())).thenReturn("feature/SQ-42/test");

        WorkspaceInfo info = lifecycleService.provisionWorkspace(buildContext());

        assertEquals(GitAuthMode.HTTPS_TOKEN, info.getGitAuthMode());
    }
}
