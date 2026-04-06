package com.squadron.workspace.service;

import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.provider.WorkspaceProvider;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic scheduler that strips any embedded credentials from git remote URLs
 * in active workspace containers. This ensures tokens don't linger in container
 * configurations beyond their useful lifetime.
 *
 * <p>Runs every 15 minutes to clean up any credentials that may have been
 * left in git remote URLs after clone or push operations.
 */
@Component
public class CredentialCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CredentialCleanupScheduler.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProvider workspaceProvider;

    public CredentialCleanupScheduler(WorkspaceRepository workspaceRepository,
                                       WorkspaceProvider workspaceProvider) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProvider = workspaceProvider;
    }

    /**
     * Strips credentials from git remote URLs in all active workspaces.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRateString = "${squadron.workspace.credential-cleanup-interval-ms:900000}")
    public void cleanupCredentials() {
        List<Workspace> activeWorkspaces = workspaceRepository.findByStatus("READY");
        if (activeWorkspaces.isEmpty()) {
            return;
        }

        log.debug("Running credential cleanup on {} active workspaces", activeWorkspaces.size());
        int cleaned = 0;

        for (Workspace workspace : activeWorkspaces) {
            if (workspace.getContainerId() == null) {
                continue;
            }
            try {
                workspaceProvider.exec(workspace.getContainerId(), new String[]{
                        "bash", "-c",
                        "git -C /workspace remote get-url origin 2>/dev/null | grep -q '://.*:.*@' && " +
                        "git -C /workspace remote set-url origin " +
                        "$(git -C /workspace remote get-url origin | sed 's|://[^@]*@|://|') || true"
                });
                cleaned++;
            } catch (Exception e) {
                log.warn("Credential cleanup failed for workspace {}: {}", workspace.getId(), e.getMessage());
            }
        }

        if (cleaned > 0) {
            log.info("Credential cleanup completed: {}/{} workspaces cleaned", cleaned, activeWorkspaces.size());
        }
    }
}
