package com.squadron.workspace.service;

import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class WorkspaceCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceCleanupScheduler.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final Duration maxIdleTime;

    public WorkspaceCleanupScheduler(WorkspaceRepository workspaceRepository,
                                      WorkspaceService workspaceService,
                                      @Value("${squadron.workspace.cleanup.max-idle-hours:24}") int maxIdleHours) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceService = workspaceService;
        this.maxIdleTime = Duration.ofHours(maxIdleHours);
    }

    @Scheduled(fixedDelayString = "${squadron.workspace.cleanup.interval-ms:3600000}")
    public void cleanupIdleWorkspaces() {
        log.info("Running workspace cleanup scheduler");
        Instant threshold = Instant.now().minus(maxIdleTime);

        List<Workspace> staleWorkspaces = workspaceRepository.findStaleWorkspaces(threshold);

        for (Workspace workspace : staleWorkspaces) {
            try {
                log.info("Cleaning up idle workspace {} (created: {})", workspace.getId(), workspace.getCreatedAt());
                workspaceService.destroyWorkspace(workspace.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup workspace {}", workspace.getId(), e);
            }
        }

        log.info("Workspace cleanup complete. Cleaned up {} workspaces.", staleWorkspaces.size());
    }
}
