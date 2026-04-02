package com.squadron.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.InvalidStateTransitionException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.workspace.dto.CreateWorkspaceRequest;
import com.squadron.workspace.dto.ExecRequest;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceDto;
import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.provider.WorkspaceProvider;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceProvider workspaceProvider;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private WorkspaceGitService workspaceGitService;

    private ObjectMapper objectMapper;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        workspaceService = new WorkspaceService(workspaceRepository, workspaceProvider, natsEventPublisher, objectMapper, workspaceGitService);
    }

    @Test
    void should_createWorkspace_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("ubuntu:22.04")
                .resourceLimits(Map.of("memory", "512Mi"))
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");

        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals(workspaceId, result.getId());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
        assertEquals("READY", result.getStatus());
        assertEquals("pod-abc123", result.getContainerId());
        assertEquals("KUBERNETES", result.getProviderType());

        verify(workspaceRepository, times(2)).save(any(Workspace.class));
        verify(workspaceProvider).createContainer(any());
        verify(natsEventPublisher).publish(eq("squadron.workspaces.lifecycle"), any());
    }

    @Test
    void should_createWorkspace_withNullResourceLimits() {
        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("DOCKER");
        when(workspaceProvider.createContainer(any())).thenReturn("container-123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals("READY", result.getStatus());
        assertNull(result.getResourceLimits());
    }

    @Test
    void should_destroyWorkspace_successfully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));

        workspaceService.destroyWorkspace(workspaceId);

        assertEquals("TERMINATED", workspace.getStatus());
        assertNotNull(workspace.getTerminatedAt());
        verify(workspaceProvider).destroyContainer("pod-abc123");
        verify(natsEventPublisher).publish(eq("squadron.workspaces.lifecycle"), any());
    }

    @Test
    void should_destroyWorkspace_whenContainerIdIsNull() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .status("CREATING")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));

        workspaceService.destroyWorkspace(workspaceId);

        assertEquals("TERMINATED", workspace.getStatus());
        verify(workspaceProvider, never()).destroyContainer(anyString());
    }

    @Test
    void should_destroyWorkspace_whenProviderThrowsException() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Provider error")).when(workspaceProvider).destroyContainer("pod-abc123");

        workspaceService.destroyWorkspace(workspaceId);

        assertEquals("TERMINATED", workspace.getStatus());
    }

    @Test
    void should_throwResourceNotFoundException_whenDestroyingNonExistent() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workspaceService.destroyWorkspace(workspaceId));
    }

    @Test
    void should_execInWorkspace_successfully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(List.of("ls", "-la"))
                .build();

        ExecResult expectedResult = ExecResult.builder()
                .exitCode(0)
                .stdout("total 0")
                .stderr("")
                .durationMs(50)
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceProvider.exec(eq("pod-abc123"), any(String[].class))).thenReturn(expectedResult);

        ExecResult result = workspaceService.execInWorkspace(request);

        assertEquals(0, result.getExitCode());
        assertEquals("total 0", result.getStdout());
    }

    @Test
    void should_execInWorkspace_whenStatusIsActive() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .containerId("container-123")
                .status("ACTIVE")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(List.of("echo", "hello"))
                .build();

        ExecResult expectedResult = ExecResult.builder().exitCode(0).stdout("hello").stderr("").durationMs(10).build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceProvider.exec(eq("container-123"), any(String[].class))).thenReturn(expectedResult);

        ExecResult result = workspaceService.execInWorkspace(request);
        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_throwInvalidStateTransition_whenExecInTerminatedWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("TERMINATED")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(List.of("ls"))
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        assertThrows(InvalidStateTransitionException.class, () -> workspaceService.execInWorkspace(request));
    }

    @Test
    void should_throwResourceNotFoundException_whenExecInNonExistent() {
        UUID workspaceId = UUID.randomUUID();
        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(List.of("ls"))
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workspaceService.execInWorkspace(request));
    }

    @Test
    void should_getWorkspace_successfully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        WorkspaceDto result = workspaceService.getWorkspace(workspaceId);

        assertNotNull(result);
        assertEquals(workspaceId, result.getId());
        assertEquals("READY", result.getStatus());
    }

    @Test
    void should_throwResourceNotFoundException_whenGettingNonExistent() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workspaceService.getWorkspace(workspaceId));
    }

    @Test
    void should_listWorkspacesByTask() {
        UUID taskId = UUID.randomUUID();
        Workspace w1 = Workspace.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).taskId(taskId)
                .userId(UUID.randomUUID()).providerType("KUBERNETES").status("READY")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();
        Workspace w2 = Workspace.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).taskId(taskId)
                .userId(UUID.randomUUID()).providerType("KUBERNETES").status("TERMINATED")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();

        when(workspaceRepository.findByTaskId(taskId)).thenReturn(List.of(w1, w2));

        List<WorkspaceDto> results = workspaceService.listWorkspacesByTask(taskId);

        assertEquals(2, results.size());
    }

    @Test
    void should_listActiveWorkspaces() {
        UUID tenantId = UUID.randomUUID();
        Workspace w1 = Workspace.builder().id(UUID.randomUUID()).tenantId(tenantId).taskId(UUID.randomUUID())
                .userId(UUID.randomUUID()).providerType("KUBERNETES").status("READY")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();

        when(workspaceRepository.findByTenantIdAndStatus(tenantId, "READY")).thenReturn(List.of(w1));

        List<WorkspaceDto> results = workspaceService.listActiveWorkspaces(tenantId);

        assertEquals(1, results.size());
        assertEquals("READY", results.get(0).getStatus());
    }

    @Test
    void should_handleNatsPublishFailure_gracefully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("NATS down")).when(natsEventPublisher).publish(anyString(), any());

        assertDoesNotThrow(() -> workspaceService.destroyWorkspace(workspaceId));
        assertEquals("TERMINATED", workspace.getStatus());
    }

    @Test
    void should_serializeAndDeserialize_resourceLimits() {
        UUID workspaceId = UUID.randomUUID();
        Map<String, Object> limits = Map.of("memory", "1Gi", "cpu", "2");

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .resourceLimits(limits)
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertNotNull(result.getResourceLimits());
        assertTrue(result.getResourceLimits().containsKey("memory"));
        assertTrue(result.getResourceLimits().containsKey("cpu"));
    }

    @Test
    void should_copyToWorkspace_successfully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        byte[] content = "hello".getBytes();
        assertDoesNotThrow(() -> workspaceService.copyToWorkspace(workspaceId, content, "/tmp/test.txt"));
        verify(workspaceProvider).copyToContainer("pod-abc123", content, "/tmp/test.txt");
    }

    @Test
    void should_throwInvalidState_whenCopyToTerminatedWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("TERMINATED")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        byte[] content = "hello".getBytes();
        assertThrows(InvalidStateTransitionException.class,
                () -> workspaceService.copyToWorkspace(workspaceId, content, "/tmp/test.txt"));
    }

    @Test
    void should_throwResourceNotFound_whenCopyToNonExistent() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        byte[] content = "hello".getBytes();
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceService.copyToWorkspace(workspaceId, content, "/tmp/test.txt"));
    }

    @Test
    void should_copyFromWorkspace_successfully() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("ACTIVE")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceProvider.copyFromContainer("pod-abc123", "/tmp/test.txt"))
                .thenReturn("file content".getBytes());

        byte[] result = workspaceService.copyFromWorkspace(workspaceId, "/tmp/test.txt");

        assertNotNull(result);
        assertEquals("file content", new String(result));
        verify(workspaceProvider).copyFromContainer("pod-abc123", "/tmp/test.txt");
    }

    @Test
    void should_throwInvalidState_whenCopyFromTerminatedWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("TERMINATED")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        assertThrows(InvalidStateTransitionException.class,
                () -> workspaceService.copyFromWorkspace(workspaceId, "/tmp/test.txt"));
    }

    @Test
    void should_throwResourceNotFound_whenCopyFromNonExistent() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workspaceService.copyFromWorkspace(workspaceId, "/tmp/test.txt"));
    }

    @Test
    void should_autoCloneRepo_afterWorkspaceCreation() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .accessToken("my-token")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();
        when(workspaceGitService.cloneRepository(workspaceId, "my-token", null)).thenReturn(cloneResult);

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals("READY", result.getStatus());
        verify(workspaceGitService).cloneRepository(workspaceId, "my-token", null);
    }

    @Test
    void should_handleAutoCloneFailure_gracefully() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .accessToken("bad-token")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        doThrow(new RuntimeException("Clone failed")).when(workspaceGitService)
                .cloneRepository(workspaceId, "bad-token", null);

        // Should not throw - auto-clone failure is handled gracefully
        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals("READY", result.getStatus());
        verify(workspaceGitService).cloneRepository(workspaceId, "bad-token", null);
    }

    @Test
    void should_skipAutoClone_whenRepoUrlIsNull() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl(null)
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("DOCKER");
        when(workspaceProvider.createContainer(any())).thenReturn("container-123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        verify(workspaceGitService, never()).cloneRepository(any(), any());
    }

    // --- SSH key passthrough tests ---

    @Test
    void should_autoCloneRepo_withSshKey() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("git@github.com:test/repo.git")
                .branch("main")
                .sshPrivateKey("-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();
        when(workspaceGitService.cloneRepository(workspaceId, null,
                "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----")).thenReturn(cloneResult);

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals("READY", result.getStatus());
        verify(workspaceGitService).cloneRepository(workspaceId, null,
                "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----");
    }

    @Test
    void should_autoCloneRepo_withAccessTokenAndSshKey_passesBoth() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("git@github.com:test/repo.git")
                .branch("main")
                .accessToken("token123")
                .sshPrivateKey("ssh-key-content")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();
        when(workspaceGitService.cloneRepository(workspaceId, "token123", "ssh-key-content")).thenReturn(cloneResult);

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        verify(workspaceGitService).cloneRepository(workspaceId, "token123", "ssh-key-content");
    }

    @Test
    void should_handleAutoCloneFailure_withSshKey_gracefully() {
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("git@github.com:test/repo.git")
                .sshPrivateKey("bad-key")
                .build();

        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any())).thenReturn("pod-abc123");
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            if (w.getId() == null) {
                w.setId(workspaceId);
                w.setCreatedAt(Instant.now());
            }
            return w;
        });

        doThrow(new RuntimeException("SSH clone failed")).when(workspaceGitService)
                .cloneRepository(workspaceId, null, "bad-key");

        WorkspaceDto result = workspaceService.createWorkspace(request);

        assertNotNull(result);
        assertEquals("READY", result.getStatus());
        verify(workspaceGitService).cloneRepository(workspaceId, null, "bad-key");
    }
}
