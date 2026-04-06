package com.squadron.agent.service;

import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.tool.builtin.CredentialClient;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.TaskContext;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceLifecycleServiceTest {

    @Mock
    private CredentialClient credentialClient;

    @Mock
    private WorkspaceClient workspaceClient;

    @Mock
    private GitClient gitClient;

    private WorkspaceLifecycleService service;

    // Reusable test identifiers
    private UUID taskId;
    private UUID tenantId;
    private UUID projectId;
    private UUID userId;
    private UUID connectionId;
    private UUID workspaceId;
    private String repoUrl;
    private String defaultBranch;
    private String taskTitle;
    private String generatedBranch;

    @BeforeEach
    void setUp() {
        service = new WorkspaceLifecycleService(credentialClient, workspaceClient, gitClient, true);

        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        repoUrl = "https://github.com/acme/my-repo.git";
        defaultBranch = "main";
        taskTitle = "Add user authentication";
        generatedBranch = "feature/SQ-42/add-user-authentication";
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private TaskContext buildTaskContext() {
        return TaskContext.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .projectId(projectId)
                .userId(userId)
                .connectionId(connectionId)
                .repoUrl(repoUrl)
                .defaultBranch(defaultBranch)
                .title(taskTitle)
                .build();
    }

    private CredentialResolutionResult buildHttpsCreds() {
        return CredentialResolutionResult.builder()
                .accessToken("ghp_abc123token")
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();
    }

    private CredentialResolutionResult buildSshCreds() {
        return CredentialResolutionResult.builder()
                .sshPrivateKey("-----BEGIN OPENSSH PRIVATE KEY-----\nfake-key\n-----END OPENSSH PRIVATE KEY-----")
                .credentialType(CredentialType.DEPLOY_KEY)
                .gitAuthMode(GitAuthMode.SSH_KEY)
                .build();
    }

    private Map<String, Object> buildWorkspaceResponse() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", workspaceId.toString());
        data.put("containerId", "container-" + workspaceId);
        data.put("status", "READY");
        return data;
    }

    private Map<String, Object> buildWorkspaceEntry(UUID id, String status, String branch) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", id.toString());
        entry.put("status", status);
        entry.put("containerId", "container-" + id);
        entry.put("branch", branch);
        return entry;
    }

    private void stubProvisioningDependencies(CredentialResolutionResult creds) {
        when(credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE))
                .thenReturn(creds);
        when(workspaceClient.createWorkspace(any())).thenReturn(buildWorkspaceResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, taskTitle))
                .thenReturn(generatedBranch);
    }

    // -----------------------------------------------------------------------
    // provisionWorkspace tests
    // -----------------------------------------------------------------------

    @Test
    void should_provisionWorkspace_withHttpsToken() {
        CredentialResolutionResult creds = buildHttpsCreds();
        stubProvisioningDependencies(creds);

        WorkspaceInfo result = service.provisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        assertEquals(generatedBranch, result.getBranchName());
        assertEquals(repoUrl, result.getRepoUrl());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
        assertEquals("container-" + workspaceId, result.getContainerId());

        // Verify the createWorkspace request map contains the access token
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workspaceClient).createWorkspace(captor.capture());
        Map<String, Object> captured = captor.getValue();
        assertEquals("ghp_abc123token", captured.get("accessToken"));
        assertNull(captured.get("sshPrivateKey"));
        assertEquals(repoUrl, captured.get("repoUrl"));
        assertEquals(defaultBranch, captured.get("branch"));
    }

    @Test
    void should_provisionWorkspace_withSshKey() {
        CredentialResolutionResult creds = buildSshCreds();
        stubProvisioningDependencies(creds);

        WorkspaceInfo result = service.provisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        assertEquals(generatedBranch, result.getBranchName());
        assertEquals(GitAuthMode.SSH_KEY, result.getGitAuthMode());

        // Verify the createWorkspace request map contains the SSH key
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workspaceClient).createWorkspace(captor.capture());
        Map<String, Object> captured = captor.getValue();
        assertNotNull(captured.get("sshPrivateKey"));
        assertTrue(captured.get("sshPrivateKey").toString().contains("BEGIN OPENSSH PRIVATE KEY"));
        assertNull(captured.get("accessToken"));
    }

    @Test
    void should_provisionWorkspace_callsCorrectSequence() {
        CredentialResolutionResult creds = buildHttpsCreds();
        stubProvisioningDependencies(creds);

        service.provisionWorkspace(buildTaskContext());

        // Verify the exact ordering: createWorkspace -> generateBranchName -> createBranch
        InOrder inOrder = inOrder(credentialClient, workspaceClient, gitClient);
        inOrder.verify(credentialClient).resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);
        inOrder.verify(workspaceClient).createWorkspace(any());
        inOrder.verify(gitClient).generateBranchName(tenantId, projectId, taskId, taskTitle);
        inOrder.verify(workspaceClient).createBranch(workspaceId, generatedBranch, defaultBranch);
    }

    @Test
    void should_provisionWorkspace_withNullDefaultBranch() {
        TaskContext ctx = buildTaskContext();
        ctx.setDefaultBranch(null);

        CredentialResolutionResult creds = buildHttpsCreds();
        when(credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE))
                .thenReturn(creds);
        when(workspaceClient.createWorkspace(any())).thenReturn(buildWorkspaceResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, taskTitle))
                .thenReturn(generatedBranch);

        WorkspaceInfo result = service.provisionWorkspace(ctx);

        assertNotNull(result);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workspaceClient).createWorkspace(captor.capture());
        Map<String, Object> captured = captor.getValue();
        assertFalse(captured.containsKey("branch"));
    }

    @Test
    void should_provisionWorkspace_withNullContainerId() {
        CredentialResolutionResult creds = buildHttpsCreds();
        when(credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE))
                .thenReturn(creds);

        Map<String, Object> wsResponse = new HashMap<>();
        wsResponse.put("id", workspaceId.toString());
        wsResponse.put("containerId", null);
        when(workspaceClient.createWorkspace(any())).thenReturn(wsResponse);
        when(gitClient.generateBranchName(tenantId, projectId, taskId, taskTitle))
                .thenReturn(generatedBranch);

        WorkspaceInfo result = service.provisionWorkspace(buildTaskContext());

        assertNull(result.getContainerId());
    }

    // -----------------------------------------------------------------------
    // findOrProvisionWorkspace tests
    // -----------------------------------------------------------------------

    @Test
    void should_findOrProvisionWorkspace_reuseExistingActive_when_activeWorkspaceExists() {
        UUID activeWsId = UUID.randomUUID();
        String existingBranch = "feature/SQ-99/existing-branch";

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(activeWsId, "ACTIVE", existingBranch)
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(activeWsId, result.getWorkspaceId());
        assertEquals(existingBranch, result.getBranchName());
        assertEquals(repoUrl, result.getRepoUrl());
        assertEquals("container-" + activeWsId, result.getContainerId());

        // Should NOT provision a new workspace
        verify(credentialClient, never()).resolveCredentials(any(), any(), any());
        verify(workspaceClient, never()).createWorkspace(any());
        verify(gitClient, never()).generateBranchName(any(), any(), any(), any());
    }

    @Test
    void should_findOrProvisionWorkspace_reuseReadyWorkspace_when_readyStatusExists() {
        UUID readyWsId = UUID.randomUUID();
        String existingBranch = "feature/SQ-50/ready-branch";

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(readyWsId, "READY", existingBranch)
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertEquals(readyWsId, result.getWorkspaceId());
        assertEquals(existingBranch, result.getBranchName());
        verify(workspaceClient, never()).createWorkspace(any());
    }

    @Test
    void should_findOrProvisionWorkspace_reuseRunningWorkspace_when_runningStatusExists() {
        UUID runningWsId = UUID.randomUUID();
        String existingBranch = "feature/SQ-55/running-branch";

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(runningWsId, "RUNNING", existingBranch)
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertEquals(runningWsId, result.getWorkspaceId());
        verify(workspaceClient, never()).createWorkspace(any());
    }

    @Test
    void should_findOrProvisionWorkspace_provisionNew_when_noActiveExists() {
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(new ArrayList<>());
        stubProvisioningDependencies(buildHttpsCreds());

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        assertEquals(generatedBranch, result.getBranchName());

        // Verify provisioning was triggered
        verify(credentialClient).resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);
        verify(workspaceClient).createWorkspace(any());
        verify(gitClient).generateBranchName(tenantId, projectId, taskId, taskTitle);
        verify(workspaceClient).createBranch(workspaceId, generatedBranch, defaultBranch);
    }

    @Test
    void should_findOrProvisionWorkspace_provisionNew_when_onlyTerminatedExists() {
        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(UUID.randomUUID(), "TERMINATED", "feature/old-branch"),
                buildWorkspaceEntry(UUID.randomUUID(), "DESTROYED", "feature/another-old-branch")
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);
        stubProvisioningDependencies(buildHttpsCreds());

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        assertEquals(generatedBranch, result.getBranchName());

        // Verify provisioning was triggered (not reused)
        verify(workspaceClient).createWorkspace(any());
    }

    @Test
    void should_findOrProvisionWorkspace_provisionNew_when_listThrowsException() {
        when(workspaceClient.listWorkspacesByTask(taskId))
                .thenThrow(new RuntimeException("Connection refused"));
        stubProvisioningDependencies(buildHttpsCreds());

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertNotNull(result);
        assertEquals(workspaceId, result.getWorkspaceId());
        // Falls back to provisioning after the exception
        verify(workspaceClient).createWorkspace(any());
    }

    @Test
    void should_findOrProvisionWorkspace_pickFirstActive_when_multipleWorkspacesExist() {
        UUID terminatedId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(terminatedId, "TERMINATED", "feature/old"),
                buildWorkspaceEntry(activeId, "ACTIVE", "feature/current")
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        assertEquals(activeId, result.getWorkspaceId());
        assertEquals("feature/current", result.getBranchName());
        verify(workspaceClient, never()).createWorkspace(any());
    }

    @Test
    void should_findOrProvisionWorkspace_handleNullStatusGracefully() {
        // A workspace with null status should not match as active
        Map<String, Object> nullStatusWs = new HashMap<>();
        nullStatusWs.put("id", UUID.randomUUID().toString());
        nullStatusWs.put("status", null);
        nullStatusWs.put("containerId", "container-x");
        nullStatusWs.put("branch", "feature/null-status");

        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(List.of(nullStatusWs));
        stubProvisioningDependencies(buildHttpsCreds());

        WorkspaceInfo result = service.findOrProvisionWorkspace(buildTaskContext());

        // Should fall through and provision new (null status != ACTIVE_STATUSES)
        assertEquals(workspaceId, result.getWorkspaceId());
        verify(workspaceClient).createWorkspace(any());
    }

    // -----------------------------------------------------------------------
    // teardownWorkspace tests
    // -----------------------------------------------------------------------

    @Test
    void should_teardownWorkspace_successfully() {
        UUID wsId = UUID.randomUUID();

        service.teardownWorkspace(wsId);

        verify(workspaceClient).destroyWorkspace(wsId);
    }

    @Test
    void should_teardownWorkspace_gracefulOnFailure_when_destroyThrows() {
        UUID wsId = UUID.randomUUID();
        doThrow(new RuntimeException("Workspace not found"))
                .when(workspaceClient).destroyWorkspace(wsId);

        // Should NOT propagate the exception
        assertDoesNotThrow(() -> service.teardownWorkspace(wsId));

        verify(workspaceClient).destroyWorkspace(wsId);
    }

    // -----------------------------------------------------------------------
    // teardownWorkspacesForTask tests
    // -----------------------------------------------------------------------

    @Test
    void should_teardownWorkspacesForTask_destroyAllWorkspaces() {
        UUID ws1 = UUID.randomUUID();
        UUID ws2 = UUID.randomUUID();
        UUID ws3 = UUID.randomUUID();

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(ws1, "ACTIVE", "feature/branch-1"),
                buildWorkspaceEntry(ws2, "READY", "feature/branch-2"),
                buildWorkspaceEntry(ws3, "TERMINATED", "feature/branch-3")
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        service.teardownWorkspacesForTask(taskId);

        verify(workspaceClient).listWorkspacesByTask(taskId);
        verify(workspaceClient).destroyWorkspace(ws1);
        verify(workspaceClient).destroyWorkspace(ws2);
        verify(workspaceClient).destroyWorkspace(ws3);
    }

    @Test
    void should_teardownWorkspacesForTask_gracefulOnListFailure_when_listThrows() {
        when(workspaceClient.listWorkspacesByTask(taskId))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Should NOT propagate the exception
        assertDoesNotThrow(() -> service.teardownWorkspacesForTask(taskId));

        verify(workspaceClient).listWorkspacesByTask(taskId);
        verify(workspaceClient, never()).destroyWorkspace(any());
    }

    @Test
    void should_teardownWorkspacesForTask_continueOnIndividualFailure() {
        UUID ws1 = UUID.randomUUID();
        UUID ws2 = UUID.randomUUID();

        List<Map<String, Object>> workspaces = List.of(
                buildWorkspaceEntry(ws1, "ACTIVE", "feature/branch-1"),
                buildWorkspaceEntry(ws2, "ACTIVE", "feature/branch-2")
        );
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(workspaces);

        // First destroy fails, second should still be attempted since teardownWorkspace
        // swallows the exception internally
        doThrow(new RuntimeException("Container stuck"))
                .when(workspaceClient).destroyWorkspace(ws1);

        assertDoesNotThrow(() -> service.teardownWorkspacesForTask(taskId));

        verify(workspaceClient).destroyWorkspace(ws1);
        verify(workspaceClient).destroyWorkspace(ws2);
    }

    @Test
    void should_teardownWorkspacesForTask_handleEmptyList() {
        when(workspaceClient.listWorkspacesByTask(taskId)).thenReturn(new ArrayList<>());

        service.teardownWorkspacesForTask(taskId);

        verify(workspaceClient).listWorkspacesByTask(taskId);
        verify(workspaceClient, never()).destroyWorkspace(any());
    }

    // -----------------------------------------------------------------------
    // YOLO mode (skip-permissions) env var tests
    // -----------------------------------------------------------------------

    @Test
    void should_passSkipPermissionsEnvVars_when_yoloModeEnabled() {
        // Service was created with skipPermissions=true in setUp()
        CredentialResolutionResult creds = buildHttpsCreds();
        stubProvisioningDependencies(creds);

        service.provisionWorkspace(buildTaskContext());

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workspaceClient).createWorkspace(captor.capture());
        Map<String, Object> captured = captor.getValue();

        // Verify that environmentVariables map was included
        assertTrue(captured.containsKey("environmentVariables"));
        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) captured.get("environmentVariables");
        assertEquals("true", envVars.get("SQUADRON_SKIP_PERMISSIONS"));
        assertEquals("1", envVars.get("DANGEROUSLY_SKIP_PERMISSIONS"));
    }

    @Test
    void should_notPassSkipPermissionsEnvVars_when_yoloModeDisabled() {
        // Create a service with skipPermissions=false
        WorkspaceLifecycleService disabledService = new WorkspaceLifecycleService(
                credentialClient, workspaceClient, gitClient, false);

        CredentialResolutionResult creds = buildHttpsCreds();
        when(credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE))
                .thenReturn(creds);
        when(workspaceClient.createWorkspace(any())).thenReturn(buildWorkspaceResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, taskTitle))
                .thenReturn(generatedBranch);

        disabledService.provisionWorkspace(buildTaskContext());

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workspaceClient).createWorkspace(captor.capture());
        Map<String, Object> captured = captor.getValue();

        // environmentVariables should NOT be present
        assertFalse(captured.containsKey("environmentVariables"));
    }
}
