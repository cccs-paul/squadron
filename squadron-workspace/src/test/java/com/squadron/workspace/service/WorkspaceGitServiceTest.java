package com.squadron.workspace.service;

import com.squadron.common.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceGitServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceProvider workspaceProvider;

    private WorkspaceGitService workspaceGitService;

    private UUID workspaceId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceGitService = new WorkspaceGitService(workspaceRepository, workspaceProvider);

        workspaceId = UUID.randomUUID();
        workspace = Workspace.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void should_cloneRepository_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        // git is already installed
        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"which", "git"}));
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}));
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}));
    }

    @Test
    void should_cloneRepository_withAccessToken() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://oauth2:my-token@github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, "my-token");

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://oauth2:my-token@github.com/test/repo.git", "/workspace"}));
    }

    @Test
    void should_cloneRepository_withBranch() {
        workspace.setBranch("feature/test");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "feature/test", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "feature/test", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}));
    }

    @Test
    void should_cloneRepository_withoutBranch() {
        workspace.setBranch(null);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "clone",
                "https://github.com/test/repo.git", "/workspace"}));
    }

    @Test
    void should_cloneRepository_installGitIfNeeded() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        // git not found
        ExecResult gitNotFound = ExecResult.builder().exitCode(1).stdout("").stderr("").durationMs(10).build();
        ExecResult installResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitNotFound);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"sh", "-c",
                "apt-get update -qq && apt-get install -y -qq git > /dev/null 2>&1"}))).thenReturn(installResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(0, result.getExitCode());
        // Verify git install was called
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"sh", "-c",
                "apt-get update -qq && apt-get install -y -qq git > /dev/null 2>&1"}));
    }

    @Test
    void should_cloneRepository_failGracefully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneFailure = ExecResult.builder().exitCode(128).stdout("")
                .stderr("fatal: repository not found").durationMs(2000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneFailure);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(128, result.getExitCode());
        assertEquals("fatal: repository not found", result.getStderr());
    }

    @Test
    void should_createBranch_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult branchResult = ExecResult.builder().exitCode(0).stdout("Switched to a new branch 'feature/new'")
                .stderr("").durationMs(50).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "checkout", "-b", "feature/new"})))
                .thenReturn(branchResult);

        ExecResult result = workspaceGitService.createBranch(workspaceId, "feature/new", null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "checkout", "-b", "feature/new"}));
    }

    @Test
    void should_createBranch_withBaseBranch() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult checkoutResult = ExecResult.builder().exitCode(0).stdout("Switched to branch 'develop'")
                .stderr("").durationMs(50).build();
        ExecResult branchResult = ExecResult.builder().exitCode(0).stdout("Switched to a new branch 'feature/new'")
                .stderr("").durationMs(50).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "checkout", "develop"})))
                .thenReturn(checkoutResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "checkout", "-b", "feature/new"})))
                .thenReturn(branchResult);

        ExecResult result = workspaceGitService.createBranch(workspaceId, "feature/new", "develop");

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "checkout", "develop"}));
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "checkout", "-b", "feature/new"}));
    }

    @Test
    void should_createBranch_returnEarlyWhenBaseCheckoutFails() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult checkoutFailure = ExecResult.builder().exitCode(1)
                .stdout("").stderr("error: pathspec 'nonexistent' did not match").durationMs(50).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "checkout", "nonexistent"})))
                .thenReturn(checkoutFailure);

        ExecResult result = workspaceGitService.createBranch(workspaceId, "feature/new", "nonexistent");

        assertEquals(1, result.getExitCode());
        // Should NOT attempt to create the new branch
        verify(workspaceProvider, never()).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "checkout", "-b", "feature/new"}));
    }

    @Test
    void should_commitChanges_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult addResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(50).build();
        ExecResult commitResult = ExecResult.builder().exitCode(0)
                .stdout("[main abc1234] Fix bug").stderr("").durationMs(100).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "add", "-A"})))
                .thenReturn(addResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "commit", "-m", "Fix bug"})))
                .thenReturn(commitResult);

        ExecResult result = workspaceGitService.commitChanges(workspaceId, "Fix bug", null, null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "add", "-A"}));
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "commit", "-m", "Fix bug"}));
    }

    @Test
    void should_commitChanges_withAuthorInfo() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult configNameResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult configEmailResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult addResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(50).build();
        ExecResult commitResult = ExecResult.builder().exitCode(0)
                .stdout("[main abc1234] Fix bug").stderr("").durationMs(100).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "config", "user.name", "Test User"})))
                .thenReturn(configNameResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "config", "user.email", "test@example.com"})))
                .thenReturn(configEmailResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "add", "-A"})))
                .thenReturn(addResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "commit", "-m", "Fix bug"})))
                .thenReturn(commitResult);

        ExecResult result = workspaceGitService.commitChanges(workspaceId, "Fix bug", "Test User", "test@example.com");

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "config", "user.name", "Test User"}));
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "config", "user.email", "test@example.com"}));
    }

    @Test
    void should_pushChanges_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "feature/new"})))
                .thenReturn(pushResult);

        ExecResult result = workspaceGitService.pushChanges(workspaceId, "feature/new", null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "push", "origin", "feature/new"}));
    }

    @Test
    void should_pushChanges_defaultToHead() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "HEAD"})))
                .thenReturn(pushResult);

        ExecResult result = workspaceGitService.pushChanges(workspaceId, null, null);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "push", "origin", "HEAD"}));
    }

    @Test
    void should_pushChanges_withAccessToken() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult setUrlResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin",
                "https://oauth2:my-token@github.com/test/repo.git"}))).thenReturn(setUrlResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "main"})))
                .thenReturn(pushResult);

        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", "my-token");

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin",
                "https://oauth2:my-token@github.com/test/repo.git"}));
    }

    @Test
    void should_getDiff_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult diffResult = ExecResult.builder().exitCode(0)
                .stdout("diff --git a/file.txt b/file.txt\n+new line").stderr("").durationMs(50).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "diff"})))
                .thenReturn(diffResult);

        ExecResult result = workspaceGitService.getDiff(workspaceId);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("diff --git"));
    }

    @Test
    void should_getStatus_successfully() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult statusResult = ExecResult.builder().exitCode(0)
                .stdout("On branch main\nnothing to commit, working tree clean").stderr("").durationMs(50).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "status"})))
                .thenReturn(statusResult);

        ExecResult result = workspaceGitService.getStatus(workspaceId);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("On branch main"));
    }

    @Test
    void should_throwResourceNotFound_whenWorkspaceNotExists() {
        UUID nonExistentId = UUID.randomUUID();
        when(workspaceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.cloneRepository(nonExistentId, null));
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.createBranch(nonExistentId, "branch", null));
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.commitChanges(nonExistentId, "msg", null, null));
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.pushChanges(nonExistentId, "main", null));
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.getDiff(nonExistentId));
        assertThrows(ResourceNotFoundException.class,
                () -> workspaceGitService.getStatus(nonExistentId));
    }

    @Test
    void should_injectTokenIntoUrl() {
        String result = workspaceGitService.injectTokenIntoUrl("https://github.com/test/repo.git", "abc123");
        assertEquals("https://oauth2:abc123@github.com/test/repo.git", result);
    }

    @Test
    void should_injectTokenIntoUrl_nonHttps() {
        String result = workspaceGitService.injectTokenIntoUrl("git@github.com:test/repo.git", "abc123");
        assertEquals("git@github.com:test/repo.git", result);
    }
}
