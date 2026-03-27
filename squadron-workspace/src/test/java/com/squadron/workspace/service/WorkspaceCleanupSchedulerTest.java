package com.squadron.workspace.service;

import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceCleanupSchedulerTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceService workspaceService;

    private WorkspaceCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkspaceCleanupScheduler(workspaceRepository, workspaceService, 24);
    }

    @Test
    void should_cleanupIdleWorkspaces() {
        UUID workspaceId1 = UUID.randomUUID();
        UUID workspaceId2 = UUID.randomUUID();

        Workspace w1 = Workspace.builder()
                .id(workspaceId1)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-1")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now().minusSeconds(86400 * 2))
                .build();

        Workspace w2 = Workspace.builder()
                .id(workspaceId2)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .containerId("container-2")
                .status("ACTIVE")
                .repoUrl("https://github.com/test/repo2.git")
                .createdAt(Instant.now().minusSeconds(86400 * 3))
                .build();

        when(workspaceRepository.findStaleWorkspaces(any(Instant.class))).thenReturn(List.of(w1, w2));

        scheduler.cleanupIdleWorkspaces();

        verify(workspaceService).destroyWorkspace(workspaceId1);
        verify(workspaceService).destroyWorkspace(workspaceId2);
    }

    @Test
    void should_handleCleanupFailureGracefully() {
        UUID workspaceId1 = UUID.randomUUID();
        UUID workspaceId2 = UUID.randomUUID();

        Workspace w1 = Workspace.builder()
                .id(workspaceId1)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-1")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now().minusSeconds(86400 * 2))
                .build();

        Workspace w2 = Workspace.builder()
                .id(workspaceId2)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .containerId("container-2")
                .status("ACTIVE")
                .repoUrl("https://github.com/test/repo2.git")
                .createdAt(Instant.now().minusSeconds(86400 * 3))
                .build();

        when(workspaceRepository.findStaleWorkspaces(any(Instant.class))).thenReturn(List.of(w1, w2));
        doThrow(new RuntimeException("Provider error")).when(workspaceService).destroyWorkspace(workspaceId1);

        // Should not throw even though first destroy fails
        assertDoesNotThrow(() -> scheduler.cleanupIdleWorkspaces());

        // Should still attempt to destroy the second workspace
        verify(workspaceService).destroyWorkspace(workspaceId1);
        verify(workspaceService).destroyWorkspace(workspaceId2);
    }

    @Test
    void should_notCleanupRecentWorkspaces() {
        when(workspaceRepository.findStaleWorkspaces(any(Instant.class))).thenReturn(Collections.emptyList());

        scheduler.cleanupIdleWorkspaces();

        verify(workspaceService, never()).destroyWorkspace(any());
    }
}
