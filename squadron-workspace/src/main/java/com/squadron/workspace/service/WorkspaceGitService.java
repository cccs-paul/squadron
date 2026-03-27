package com.squadron.workspace.service;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.provider.WorkspaceProvider;
import com.squadron.workspace.repository.WorkspaceRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages git operations within workspace containers by executing git CLI commands
 * inside the sandboxed environment.
 */
@Service
public class WorkspaceGitService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGitService.class);
    private static final String WORKSPACE_DIR = "/workspace";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProvider workspaceProvider;

    public WorkspaceGitService(WorkspaceRepository workspaceRepository,
                                WorkspaceProvider workspaceProvider) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProvider = workspaceProvider;
    }

    /**
     * Clones a repository into the workspace container. Installs git if needed,
     * then clones the repo to /workspace directory.
     */
    @Transactional
    public ExecResult cloneRepository(UUID workspaceId, String accessToken) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Cloning repo {} into workspace {}", workspace.getRepoUrl(), workspaceId);

        // 1. Ensure git is installed
        ensureGitInstalled(containerId);

        // 2. Create workspace directory
        workspaceProvider.exec(containerId, new String[]{"mkdir", "-p", WORKSPACE_DIR});

        // 3. Build clone URL (inject token if provided)
        String cloneUrl = workspace.getRepoUrl();
        if (accessToken != null && !accessToken.isBlank()) {
            cloneUrl = injectTokenIntoUrl(cloneUrl, accessToken);
        }

        // 4. Clone the repo
        String[] cloneCmd = workspace.getBranch() != null
                ? new String[]{"git", "clone", "--branch", workspace.getBranch(), "--single-branch", cloneUrl, WORKSPACE_DIR}
                : new String[]{"git", "clone", cloneUrl, WORKSPACE_DIR};

        ExecResult result = workspaceProvider.exec(containerId, cloneCmd);

        if (result.getExitCode() == 0) {
            log.info("Successfully cloned repo into workspace {}", workspaceId);
        } else {
            log.error("Failed to clone repo into workspace {}: {}", workspaceId, sanitizeOutput(result.getStderr()));
        }

        return result;
    }

    /**
     * Creates a new branch in the workspace repository.
     */
    @Transactional(readOnly = true)
    public ExecResult createBranch(UUID workspaceId, String branchName, String baseBranch) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Creating branch {} in workspace {}", branchName, workspaceId);

        // Checkout base branch if specified
        if (baseBranch != null && !baseBranch.isBlank()) {
            ExecResult checkoutResult = workspaceProvider.exec(containerId,
                    new String[]{"git", "-C", WORKSPACE_DIR, "checkout", baseBranch});
            if (checkoutResult.getExitCode() != 0) {
                return checkoutResult;
            }
        }

        // Create and checkout new branch
        return workspaceProvider.exec(containerId,
                new String[]{"git", "-C", WORKSPACE_DIR, "checkout", "-b", branchName});
    }

    /**
     * Commits all changes in the workspace repository.
     */
    @Transactional(readOnly = true)
    public ExecResult commitChanges(UUID workspaceId, String message, String authorName, String authorEmail) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Committing changes in workspace {}", workspaceId);

        // Configure git author
        if (authorName != null) {
            workspaceProvider.exec(containerId,
                    new String[]{"git", "-C", WORKSPACE_DIR, "config", "user.name", authorName});
        }
        if (authorEmail != null) {
            workspaceProvider.exec(containerId,
                    new String[]{"git", "-C", WORKSPACE_DIR, "config", "user.email", authorEmail});
        }

        // Stage all changes
        workspaceProvider.exec(containerId,
                new String[]{"git", "-C", WORKSPACE_DIR, "add", "-A"});

        // Commit
        return workspaceProvider.exec(containerId,
                new String[]{"git", "-C", WORKSPACE_DIR, "commit", "-m", message});
    }

    /**
     * Pushes changes from the workspace to the remote.
     */
    @Transactional(readOnly = true)
    public ExecResult pushChanges(UUID workspaceId, String branch, String accessToken) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Pushing changes from workspace {}", workspaceId);

        // If access token provided, set remote URL with token
        if (accessToken != null && !accessToken.isBlank()) {
            String remoteUrl = injectTokenIntoUrl(workspace.getRepoUrl(), accessToken);
            workspaceProvider.exec(containerId,
                    new String[]{"git", "-C", WORKSPACE_DIR, "remote", "set-url", "origin", remoteUrl});
        }

        String pushBranch = branch != null ? branch : "HEAD";
        return workspaceProvider.exec(containerId,
                new String[]{"git", "-C", WORKSPACE_DIR, "push", "origin", pushBranch});
    }

    /**
     * Returns the git diff output for the workspace repository.
     */
    @Transactional(readOnly = true)
    public ExecResult getDiff(UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);
        return workspaceProvider.exec(workspace.getContainerId(),
                new String[]{"git", "-C", WORKSPACE_DIR, "diff"});
    }

    /**
     * Returns the current git status in the workspace.
     */
    @Transactional(readOnly = true)
    public ExecResult getStatus(UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);
        return workspaceProvider.exec(workspace.getContainerId(),
                new String[]{"git", "-C", WORKSPACE_DIR, "status"});
    }

    private Workspace getWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private void ensureGitInstalled(String containerId) {
        ExecResult check = workspaceProvider.exec(containerId, new String[]{"which", "git"});
        if (check.getExitCode() != 0) {
            log.info("Git not found, installing...");
            workspaceProvider.exec(containerId,
                    new String[]{"sh", "-c", "apt-get update -qq && apt-get install -y -qq git > /dev/null 2>&1"});
        }
    }

    String injectTokenIntoUrl(String url, String token) {
        if (url.startsWith("https://")) {
            return url.replace("https://", "https://oauth2:" + token + "@");
        }
        return url;
    }

    private String sanitizeOutput(String output) {
        if (output == null) return "";
        // Remove anything that looks like a token in URLs
        return output.replaceAll("oauth2:[^@]+@", "oauth2:***@");
    }
}
