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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: OAuth2 credential flow including token refresh scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 Credential Flow Integration")
class OAuth2CredentialFlowIntegrationTest {

    @Mock private CredentialClient credentialClient;
    @Mock private WorkspaceClient workspaceClient;
    @Mock private GitClient gitClient;

    private WorkspaceLifecycleService lifecycleService;

    private UUID taskId, tenantId, projectId, userId, connectionId, workspaceId;

    @BeforeEach
    void setUp() {
        lifecycleService = new WorkspaceLifecycleService(credentialClient, workspaceClient, gitClient, true);
        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
    }

    private TaskContext buildContext() {
        return TaskContext.builder()
                .taskId(taskId).tenantId(tenantId).projectId(projectId)
                .userId(userId).connectionId(connectionId)
                .repoUrl("https://github.com/acme/oauth-repo.git")
                .defaultBranch("main")
                .branchStrategy("GITHUB_FLOW")
                .branchNamingTemplate("{strategy}/{ticket}-{description}")
                .externalId("SQ-100").title("OAuth2 feature")
                .build();
    }

    @Test
    @DisplayName("should resolve OAuth2 token and provision workspace successfully")
    void should_completeOAuth2Flow_when_tokenIsValid() {
        var creds = CredentialResolutionResult.builder()
                .accessToken("gho_oauth2_valid_token")
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(any(), any(), any(), anyString()))
                .thenReturn("feature/SQ-100/oauth2-feature");

        WorkspaceInfo info = lifecycleService.provisionWorkspace(buildContext());

        assertNotNull(info);
        assertEquals(workspaceId, info.getWorkspaceId());
        assertEquals(GitAuthMode.HTTPS_TOKEN, info.getGitAuthMode());
    }

    @Test
    @DisplayName("should resolve refreshed OAuth2 token transparently")
    void should_useRefreshedToken_when_originalTokenExpired() {
        // The credential resolution service handles refresh internally,
        // so the caller always gets a valid token back
        var creds = CredentialResolutionResult.builder()
                .accessToken("gho_refreshed_new_token")
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(any(), any(), any(), anyString())).thenReturn("feature/SQ-100/test");

        WorkspaceInfo info = lifecycleService.provisionWorkspace(buildContext());

        assertNotNull(info);
        assertEquals(workspaceId, info.getWorkspaceId());
        verify(credentialClient).resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);
    }

    @Test
    @DisplayName("should handle OAuth2 token with expiry set")
    void should_acceptTokenWithExpiry_when_expiresAtIsSet() {
        Instant expiry = Instant.now().plusSeconds(1800);
        var creds = CredentialResolutionResult.builder()
                .accessToken("gho_short_lived_token")
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .expiresAt(expiry)
                .build();

        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("status", "READY");
        when(workspaceClient.createWorkspace(anyMap())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(any(), any(), any(), anyString())).thenReturn("feature/SQ-100/test");

        WorkspaceInfo info = lifecycleService.provisionWorkspace(buildContext());

        assertNotNull(info);
        assertEquals(GitAuthMode.HTTPS_TOKEN, info.getGitAuthMode());
    }

    @Test
    @DisplayName("should propagate error when OAuth2 refresh fails completely")
    void should_propagateError_when_refreshFailsEntirely() {
        when(credentialClient.resolveCredentials(eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenThrow(new RuntimeException("OAuth2 token expired and refresh failed"));

        assertThrows(RuntimeException.class, () -> lifecycleService.provisionWorkspace(buildContext()));
        verify(workspaceClient, never()).createWorkspace(anyMap());
    }
}
