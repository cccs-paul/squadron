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
 * inside the sandboxed environment. Supports both HTTPS (with OAuth2 token) and
 * SSH (with private key) authentication.
 */
@Service
public class WorkspaceGitService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGitService.class);
    private static final String WORKSPACE_DIR = "/workspace";
    private static final String SSH_KEY_PATH = "/tmp/.squadron_ssh_key";
    private static final String GIT_SSH_COMMAND = "ssh -i " + SSH_KEY_PATH + " -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProvider workspaceProvider;

    public WorkspaceGitService(WorkspaceRepository workspaceRepository,
                                WorkspaceProvider workspaceProvider) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProvider = workspaceProvider;
    }

    /**
     * Clones a repository into the workspace container. Installs git if needed,
     * then clones the repo to /workspace directory. Supports both HTTPS (with
     * access token) and SSH (with private key) authentication.
     */
    @Transactional
    public ExecResult cloneRepository(UUID workspaceId, String accessToken) {
        return cloneRepository(workspaceId, accessToken, null);
    }

    /**
     * Clones a repository into the workspace container with optional SSH key support.
     */
    @Transactional
    public ExecResult cloneRepository(UUID workspaceId, String accessToken, String sshPrivateKey) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Cloning repo {} into workspace {}", workspace.getRepoUrl(), workspaceId);

        // 1. Ensure git is installed
        ensureGitInstalled(containerId);

        // 2. Create workspace directory
        workspaceProvider.exec(containerId, new String[]{"mkdir", "-p", WORKSPACE_DIR});

        // 3. Set up SSH key if provided and URL is SSH
        boolean usingSsh = false;
        if (sshPrivateKey != null && !sshPrivateKey.isBlank() && isSshUrl(workspace.getRepoUrl())) {
            setupSshKey(containerId, sshPrivateKey);
            usingSsh = true;
        }

        try {
            // 4. Build clone URL (inject token if HTTPS, use as-is for SSH)
            String cloneUrl = workspace.getRepoUrl();
            if (!usingSsh && accessToken != null && !accessToken.isBlank()) {
                cloneUrl = injectTokenIntoUrl(cloneUrl, accessToken);
            }

            // 5. Clone the repo
            String[] cloneCmd = workspace.getBranch() != null
                    ? new String[]{"git", "clone", "--branch", workspace.getBranch(), "--single-branch", cloneUrl, WORKSPACE_DIR}
                    : new String[]{"git", "clone", cloneUrl, WORKSPACE_DIR};

            ExecResult result;
            if (usingSsh) {
                result = execWithSshCommand(containerId, cloneCmd);
            } else {
                result = workspaceProvider.exec(containerId, cloneCmd);
            }

            if (result.getExitCode() == 0) {
                log.info("Successfully cloned repo into workspace {}", workspaceId);
            } else {
                log.error("Failed to clone repo into workspace {}: {}", workspaceId, sanitizeOutput(result.getStderr()));
            }

            return result;
        } finally {
            if (usingSsh) {
                cleanupSshKey(containerId);
            }
        }
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
        return pushChanges(workspaceId, branch, accessToken, null);
    }

    /**
     * Pushes changes from the workspace to the remote with optional SSH key support.
     */
    @Transactional(readOnly = true)
    public ExecResult pushChanges(UUID workspaceId, String branch, String accessToken, String sshPrivateKey) {
        Workspace workspace = getWorkspace(workspaceId);
        String containerId = workspace.getContainerId();

        log.info("Pushing changes from workspace {}", workspaceId);

        boolean usingSsh = false;
        if (sshPrivateKey != null && !sshPrivateKey.isBlank() && isSshUrl(workspace.getRepoUrl())) {
            setupSshKey(containerId, sshPrivateKey);
            usingSsh = true;
        }

        try {
            // If access token provided (HTTPS), set remote URL with token
            if (!usingSsh && accessToken != null && !accessToken.isBlank()) {
                String remoteUrl = injectTokenIntoUrl(workspace.getRepoUrl(), accessToken);
                workspaceProvider.exec(containerId,
                        new String[]{"git", "-C", WORKSPACE_DIR, "remote", "set-url", "origin", remoteUrl});
            }

            String pushBranch = branch != null ? branch : "HEAD";
            String[] pushCmd = new String[]{"git", "-C", WORKSPACE_DIR, "push", "origin", pushBranch};

            if (usingSsh) {
                return execWithSshCommand(containerId, pushCmd);
            } else {
                return workspaceProvider.exec(containerId, pushCmd);
            }
        } finally {
            if (usingSsh) {
                cleanupSshKey(containerId);
            }
        }
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

    /**
     * Determines if a URL is an SSH-style git URL.
     * Matches: git@host:path, ssh://user@host/path
     */
    boolean isSshUrl(String url) {
        if (url == null) return false;
        return url.startsWith("git@") || url.startsWith("ssh://");
    }

    /**
     * Writes the SSH private key to a temp file inside the container and sets
     * appropriate permissions.
     */
    void setupSshKey(String containerId, String sshPrivateKey) {
        log.debug("Setting up SSH key in container {}", containerId);
        // Ensure the key ends with a newline (required by OpenSSH)
        String key = sshPrivateKey.endsWith("\n") ? sshPrivateKey : sshPrivateKey + "\n";
        // Write the key file
        workspaceProvider.exec(containerId,
                new String[]{"sh", "-c", "printf '%s' '" + escapeForShell(key) + "' > " + SSH_KEY_PATH});
        // Set permissions
        workspaceProvider.exec(containerId,
                new String[]{"chmod", "600", SSH_KEY_PATH});
    }

    /**
     * Removes the SSH key file from the container.
     */
    void cleanupSshKey(String containerId) {
        log.debug("Cleaning up SSH key from container {}", containerId);
        workspaceProvider.exec(containerId,
                new String[]{"rm", "-f", SSH_KEY_PATH});
    }

    /**
     * Executes a git command with GIT_SSH_COMMAND environment variable set
     * for SSH key authentication.
     */
    private ExecResult execWithSshCommand(String containerId, String[] gitCmd) {
        // Wrap the git command with GIT_SSH_COMMAND env var
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("GIT_SSH_COMMAND='").append(GIT_SSH_COMMAND).append("' ");
        for (int i = 0; i < gitCmd.length; i++) {
            if (i > 0) cmdBuilder.append(" ");
            // Quote arguments that might contain special chars
            cmdBuilder.append("'").append(gitCmd[i].replace("'", "'\\''")).append("'");
        }
        return workspaceProvider.exec(containerId,
                new String[]{"sh", "-c", cmdBuilder.toString()});
    }

    /**
     * Escapes a string for safe inclusion in a single-quoted shell string.
     */
    private String escapeForShell(String value) {
        // In single quotes, only single quote needs escaping: replace ' with '\''
        return value.replace("'", "'\\''");
    }

    private String sanitizeOutput(String output) {
        if (output == null) return "";
        // Remove anything that looks like a token in URLs
        return output.replaceAll("oauth2:[^@]+@", "oauth2:***@");
    }
}
