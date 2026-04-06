package com.squadron.agent.service;

import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.tool.builtin.CredentialClient;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.TaskContext;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.GitAuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages workspace lifecycle for agent tasks: provision, reuse, and teardown.
 *
 * <p>When a coding/review/QA agent starts work, this service:
 * <ol>
 *   <li>Resolves credentials for the user/connection via {@link CredentialClient}</li>
 *   <li>Creates a workspace container via {@link WorkspaceClient}</li>
 *   <li>Generates and checks out a feature branch via {@link GitClient}</li>
 * </ol>
 *
 * <p>For review/QA agents, it can reuse an existing workspace for the same task.
 * After merge, it tears down the workspace.
 */
@Service
public class WorkspaceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLifecycleService.class);
    private static final Set<String> ACTIVE_STATUSES = Set.of("READY", "ACTIVE", "RUNNING");

    private final CredentialClient credentialClient;
    private final WorkspaceClient workspaceClient;
    private final GitClient gitClient;
    private final boolean skipPermissions;

    public WorkspaceLifecycleService(CredentialClient credentialClient,
                                      WorkspaceClient workspaceClient,
                                      GitClient gitClient,
                                      @Value("${squadron.agents.skip-permissions:true}") boolean skipPermissions) {
        this.credentialClient = credentialClient;
        this.workspaceClient = workspaceClient;
        this.gitClient = gitClient;
        this.skipPermissions = skipPermissions;
    }

    /**
     * Provisions a new workspace for a task: resolves credentials, creates the
     * workspace container (which auto-clones the repo), generates a branch name,
     * and creates the branch.
     *
     * @param taskContext enriched task context from the orchestrator
     * @return information about the provisioned workspace
     */
    public WorkspaceInfo provisionWorkspace(TaskContext taskContext) {
        log.info("Provisioning workspace for task {} in project {}",
                taskContext.getTaskId(), taskContext.getProjectId());

        // 1. Resolve credentials for git clone
        CredentialResolutionResult creds = credentialClient.resolveCredentials(
                taskContext.getUserId(), taskContext.getConnectionId(), CredentialPurpose.GIT_CLONE);
        log.debug("Resolved credentials: type={}, authMode={}",
                creds.getCredentialType(), creds.getGitAuthMode());

        // 2. Create workspace (auto-clones during creation)
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("tenantId", taskContext.getTenantId().toString());
        createRequest.put("taskId", taskContext.getTaskId().toString());
        createRequest.put("userId", taskContext.getUserId().toString());
        createRequest.put("repoUrl", taskContext.getRepoUrl());
        if (taskContext.getDefaultBranch() != null) {
            createRequest.put("branch", taskContext.getDefaultBranch());
        }
        if (creds.getGitAuthMode() == GitAuthMode.HTTPS_TOKEN && creds.getAccessToken() != null) {
            createRequest.put("accessToken", creds.getAccessToken());
        }
        if (creds.getGitAuthMode() == GitAuthMode.SSH_KEY && creds.getSshPrivateKey() != null) {
            createRequest.put("sshPrivateKey", creds.getSshPrivateKey());
        }

        // Pass skip-permissions env vars to workspace container
        if (skipPermissions) {
            Map<String, String> envVars = Map.of(
                    "SQUADRON_SKIP_PERMISSIONS", "true",
                    "DANGEROUSLY_SKIP_PERMISSIONS", "1"
            );
            createRequest.put("environmentVariables", envVars);
        }

        Map<String, Object> workspaceData = workspaceClient.createWorkspace(createRequest);
        UUID workspaceId = UUID.fromString(workspaceData.get("id").toString());
        String containerId = workspaceData.get("containerId") != null
                ? workspaceData.get("containerId").toString() : null;

        log.info("Workspace {} created for task {}", workspaceId, taskContext.getTaskId());

        // 3. Generate branch name
        String branchName = gitClient.generateBranchName(
                taskContext.getTenantId(), taskContext.getProjectId(),
                taskContext.getTaskId(), taskContext.getTitle());
        log.info("Generated branch name: {}", branchName);

        // 4. Create branch in workspace
        workspaceClient.createBranch(workspaceId, branchName, taskContext.getDefaultBranch());
        log.info("Branch {} created in workspace {}", branchName, workspaceId);

        return WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .repoUrl(taskContext.getRepoUrl())
                .gitAuthMode(creds.getGitAuthMode())
                .containerId(containerId)
                .build();
    }

    /**
     * Finds an existing active workspace for a task, or provisions a new one
     * if none exists. Used by review and QA agents that reuse the coding
     * agent's workspace.
     *
     * @param taskContext enriched task context from the orchestrator
     * @return information about the workspace (existing or newly provisioned)
     */
    public WorkspaceInfo findOrProvisionWorkspace(TaskContext taskContext) {
        log.debug("Looking for existing workspace for task {}", taskContext.getTaskId());

        try {
            List<Map<String, Object>> workspaces = workspaceClient.listWorkspacesByTask(
                    taskContext.getTaskId());

            // Find an active workspace
            for (Map<String, Object> ws : workspaces) {
                String status = ws.get("status") != null ? ws.get("status").toString() : "";
                if (ACTIVE_STATUSES.contains(status.toUpperCase())) {
                    UUID workspaceId = UUID.fromString(ws.get("id").toString());
                    String containerId = ws.get("containerId") != null
                            ? ws.get("containerId").toString() : null;
                    String branchName = ws.get("branch") != null
                            ? ws.get("branch").toString() : null;

                    log.info("Reusing existing workspace {} (status={}) for task {}",
                            workspaceId, status, taskContext.getTaskId());

                    return WorkspaceInfo.builder()
                            .workspaceId(workspaceId)
                            .branchName(branchName)
                            .repoUrl(taskContext.getRepoUrl())
                            .containerId(containerId)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list workspaces for task {}: {}", taskContext.getTaskId(), e.getMessage());
        }

        // No active workspace found — provision a new one
        log.info("No active workspace found for task {}, provisioning new one", taskContext.getTaskId());
        return provisionWorkspace(taskContext);
    }

    /**
     * Tears down (destroys) a workspace after a task is complete (e.g. after merge).
     *
     * @param workspaceId the workspace to destroy
     */
    public void teardownWorkspace(UUID workspaceId) {
        log.info("Tearing down workspace {}", workspaceId);
        try {
            workspaceClient.destroyWorkspace(workspaceId);
            log.info("Workspace {} torn down successfully", workspaceId);
        } catch (Exception e) {
            log.warn("Failed to tear down workspace {}: {}", workspaceId, e.getMessage());
        }
    }

    /**
     * Tears down all workspaces for a task.
     *
     * @param taskId the task whose workspaces to destroy
     */
    public void teardownWorkspacesForTask(UUID taskId) {
        log.info("Tearing down all workspaces for task {}", taskId);
        try {
            List<Map<String, Object>> workspaces = workspaceClient.listWorkspacesByTask(taskId);
            for (Map<String, Object> ws : workspaces) {
                UUID wsId = UUID.fromString(ws.get("id").toString());
                teardownWorkspace(wsId);
            }
        } catch (Exception e) {
            log.warn("Failed to tear down workspaces for task {}: {}", taskId, e.getMessage());
        }
    }
}
