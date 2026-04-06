package com.squadron.workspace.service;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.provider.WorkspaceProvider;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialCleanupSchedulerTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceProvider workspaceProvider;

    private CredentialCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CredentialCleanupScheduler(workspaceRepository, workspaceProvider);
    }

    private Workspace buildWorkspace(String containerId) {
        return Workspace.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId(containerId)
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void should_stripCredentials_from_activeWorkspaces() {
        Workspace ws1 = buildWorkspace("pod-1");
        Workspace ws2 = buildWorkspace("pod-2");
        when(workspaceRepository.findByStatus("READY")).thenReturn(List.of(ws1, ws2));

        ExecResult success = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq("pod-1"), any(String[].class))).thenReturn(success);
        when(workspaceProvider.exec(eq("pod-2"), any(String[].class))).thenReturn(success);

        scheduler.cleanupCredentials();

        verify(workspaceProvider).exec(eq("pod-1"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("bash") && cmd[1].equals("-c")
                && cmd[2].contains("remote set-url origin")));
        verify(workspaceProvider).exec(eq("pod-2"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("bash") && cmd[1].equals("-c")
                && cmd[2].contains("remote set-url origin")));
    }

    @Test
    void should_skipWorkspaces_withNoContainerId() {
        Workspace wsNoContainer = buildWorkspace(null);
        Workspace wsWithContainer = buildWorkspace("pod-1");
        when(workspaceRepository.findByStatus("READY")).thenReturn(List.of(wsNoContainer, wsWithContainer));

        ExecResult success = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq("pod-1"), any(String[].class))).thenReturn(success);

        scheduler.cleanupCredentials();

        // Should only exec on the workspace with a containerId
        verify(workspaceProvider).exec(eq("pod-1"), any(String[].class));
        verify(workspaceProvider, never()).exec(eq(null), any(String[].class));
    }

    @Test
    void should_handleExecFailure_gracefully() {
        Workspace ws = buildWorkspace("pod-fail");
        when(workspaceRepository.findByStatus("READY")).thenReturn(List.of(ws));
        when(workspaceProvider.exec(eq("pod-fail"), any(String[].class)))
                .thenThrow(new RuntimeException("Container not found"));

        // Should not throw
        scheduler.cleanupCredentials();

        verify(workspaceProvider).exec(eq("pod-fail"), any(String[].class));
    }

    @Test
    void should_doNothing_when_noActiveWorkspaces() {
        when(workspaceRepository.findByStatus("READY")).thenReturn(List.of());

        scheduler.cleanupCredentials();

        verifyNoInteractions(workspaceProvider);
    }

    @Test
    void should_continueOnFailure_when_oneWorkspaceFails() {
        Workspace ws1 = buildWorkspace("pod-fail");
        Workspace ws2 = buildWorkspace("pod-ok");
        when(workspaceRepository.findByStatus("READY")).thenReturn(List.of(ws1, ws2));

        when(workspaceProvider.exec(eq("pod-fail"), any(String[].class)))
                .thenThrow(new RuntimeException("Exec failed"));
        ExecResult success = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq("pod-ok"), any(String[].class))).thenReturn(success);

        scheduler.cleanupCredentials();

        // Both workspaces should be attempted
        verify(workspaceProvider).exec(eq("pod-fail"), any(String[].class));
        verify(workspaceProvider).exec(eq("pod-ok"), any(String[].class));
    }
}
