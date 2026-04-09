package com.squadron.workspace.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.TestGitAccessResult;
import com.squadron.workspace.dto.WorkspaceSpec;
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
import static org.mockito.ArgumentMatchers.argThat;
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

    // --- SSH URL detection tests ---

    @Test
    void should_detectSshUrl_whenGitAtFormat() {
        assertTrue(workspaceGitService.isSshUrl("git@github.com:test/repo.git"));
    }

    @Test
    void should_detectSshUrl_whenSshProtocol() {
        assertTrue(workspaceGitService.isSshUrl("ssh://git@github.com/test/repo.git"));
    }

    @Test
    void should_notDetectSshUrl_whenHttps() {
        assertFalse(workspaceGitService.isSshUrl("https://github.com/test/repo.git"));
    }

    @Test
    void should_notDetectSshUrl_whenNull() {
        assertFalse(workspaceGitService.isSshUrl(null));
    }

    // --- SSH clone tests ---

    @Test
    void should_cloneRepository_withSshKey() {
        workspace.setRepoUrl("git@github.com:test/repo.git");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult writeKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult chmodResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();
        ExecResult rmKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        // SSH key setup
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("printf") && cmd[2].contains("/tmp/.squadron_ssh_key")))).thenReturn(writeKeyResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}))).thenReturn(chmodResult);
        // Clone via SSH (uses sh -c with GIT_SSH_COMMAND)
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("GIT_SSH_COMMAND") && cmd[2].contains("clone")))).thenReturn(cloneResult);
        // Cleanup
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}))).thenReturn(rmKeyResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null, sshKey);

        assertEquals(0, result.getExitCode());
        // Verify SSH key setup and cleanup happened
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}));
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}));
    }

    @Test
    void should_cloneRepository_withSshKey_cleanupOnFailure() {
        workspace.setRepoUrl("git@github.com:test/repo.git");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult writeKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult chmodResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneFailure = ExecResult.builder().exitCode(128).stdout("").stderr("Permission denied (publickey)").durationMs(2000).build();
        ExecResult rmKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("printf")))).thenReturn(writeKeyResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}))).thenReturn(chmodResult);
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("GIT_SSH_COMMAND")))).thenReturn(cloneFailure);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}))).thenReturn(rmKeyResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null, sshKey);

        assertEquals(128, result.getExitCode());
        // Verify cleanup still happened even on failure
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}));
    }

    @Test
    void should_cloneRepository_ignoreSshKey_whenUrlIsHttps() {
        // HTTPS URL + SSH key provided -> should use HTTPS, ignore SSH key
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://oauth2:token123@github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.cloneRepository(workspaceId, "token123", sshKey);

        assertEquals(0, result.getExitCode());
        // Should NOT set up SSH key for HTTPS URLs
        verify(workspaceProvider, never()).exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}));
    }

    // --- SSH push tests ---

    @Test
    void should_pushChanges_withSshKey() {
        workspace.setRepoUrl("git@github.com:test/repo.git");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult writeKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult chmodResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();
        ExecResult rmKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();

        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("printf")))).thenReturn(writeKeyResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}))).thenReturn(chmodResult);
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("GIT_SSH_COMMAND") && cmd[2].contains("push")))).thenReturn(pushResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}))).thenReturn(rmKeyResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", null, sshKey);

        assertEquals(0, result.getExitCode());
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}));
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}));
    }

    @Test
    void should_pushChanges_withSshKey_cleanupOnFailure() {
        workspace.setRepoUrl("git@github.com:test/repo.git");
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult writeKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult chmodResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult pushFailure = ExecResult.builder().exitCode(1).stdout("").stderr("Permission denied").durationMs(3000).build();
        ExecResult rmKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();

        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("printf")))).thenReturn(writeKeyResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}))).thenReturn(chmodResult);
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("GIT_SSH_COMMAND")))).thenReturn(pushFailure);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}))).thenReturn(rmKeyResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", null, sshKey);

        assertEquals(1, result.getExitCode());
        // Verify cleanup happened even on failure
        verify(workspaceProvider).exec(eq("pod-abc123"), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}));
    }

    @Test
    void should_pushChanges_ignoreSshKey_whenUrlIsHttps() {
        // HTTPS URL + SSH key -> should use HTTPS token, not SSH
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult setUrlResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin",
                "https://oauth2:my-token@github.com/test/repo.git"}))).thenReturn(setUrlResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "main"})))
                .thenReturn(pushResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", "my-token", sshKey);

        assertEquals(0, result.getExitCode());
        // Should NOT set up SSH key for HTTPS URLs
        verify(workspaceProvider, never()).exec(eq("pod-abc123"), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}));
    }

    // --- Two-arg overload delegates ---

    @Test
    void should_cloneRepository_twoArgDelegatesToThreeArg() {
        // The 2-arg overload should call the 3-arg overload with null sshPrivateKey
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        ExecResult mkdirResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"mkdir", "-p", "/workspace"}))).thenReturn(mkdirResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "clone", "--branch", "main", "--single-branch",
                "https://github.com/test/repo.git", "/workspace"}))).thenReturn(cloneResult);

        ExecResult result = workspaceGitService.cloneRepository(workspaceId, null);

        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_pushChanges_threeArgDelegatesToFourArg() {
        // The 3-arg overload should call the 4-arg overload with null sshPrivateKey
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "main"})))
                .thenReturn(pushResult);

        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", null);

        assertEquals(0, result.getExitCode());
    }

    // ========================================================================
    // Security: Token stripping after push (credential TTL)
    // ========================================================================

    @Test
    void should_stripTokenFromRemoteUrl_afterHttpsPush() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult setUrlResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        // Mock the set-url call with token (during push)
        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 7 && cmd[0].equals("git") && cmd[4].equals("set-url") && cmd[6].contains("oauth2:"))))
                .thenReturn(setUrlResult);
        // Mock the push call
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "feature"})))
                .thenReturn(pushResult);
        // Mock the strip-url call (restoring clean URL)
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin", "https://github.com/test/repo.git"})))
                .thenReturn(setUrlResult);

        ExecResult result = workspaceGitService.pushChanges(workspaceId, "feature", "my-secret-token");

        assertEquals(0, result.getExitCode());
        // Verify the clean URL was set after push
        verify(workspaceProvider).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin", "https://github.com/test/repo.git"}));
    }

    @Test
    void should_notStripToken_whenPushWithNoToken() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "main"})))
                .thenReturn(pushResult);

        workspaceGitService.pushChanges(workspaceId, "main", null, null);

        // Should NOT attempt to strip token (no set-url call with clean URL)
        verify(workspaceProvider, never()).exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin", "https://github.com/test/repo.git"}));
    }

    @Test
    void should_stripTokenGracefully_whenStripFails() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ExecResult setUrlResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(10).build();
        ExecResult pushResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceProvider.exec(eq("pod-abc123"), argThat(cmd ->
                cmd.length == 7 && cmd[0].equals("git") && cmd[4].equals("set-url") && cmd[6].contains("oauth2:"))))
                .thenReturn(setUrlResult);
        when(workspaceProvider.exec(eq("pod-abc123"), eq(new String[]{"git", "-C", "/workspace", "push", "origin", "main"})))
                .thenReturn(pushResult);
        // Strip call throws exception
        when(workspaceProvider.exec(eq("pod-abc123"),
                eq(new String[]{"git", "-C", "/workspace", "remote", "set-url", "origin", "https://github.com/test/repo.git"})))
                .thenThrow(new RuntimeException("Container not responding"));

        // Should not throw — stripping failure is non-fatal
        ExecResult result = workspaceGitService.pushChanges(workspaceId, "main", "my-token");
        assertEquals(0, result.getExitCode());
    }

    // ========================================================================
    // testGitAccess tests
    // ========================================================================

    @Test
    void should_testGitAccess_successWithHttpsToken() {
        String containerId = "test-container-123";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        // git is installed
        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        // clone succeeds
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(2000).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"git", "clone", "--depth", "1", "--single-branch",
                "--branch", "main", "https://oauth2:my-token@github.com/test/repo.git", "/tmp/test-clone"}))).thenReturn(cloneResult);

        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "https://github.com/test/repo.git", "my-token", null, "main");

        assertTrue(result.isSuccess());
        assertEquals("Git repository is accessible", result.getMessage());
        assertEquals("main", result.getBranch());
        assertTrue(result.getDurationMs() >= 0);

        // Verify container was destroyed
        verify(workspaceProvider).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_successWithSshKey() {
        String containerId = "test-container-456";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        // git is installed
        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        // SSH key setup
        ExecResult writeKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        ExecResult chmodResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        when(workspaceProvider.exec(eq(containerId), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("printf") && cmd[2].contains("/tmp/.squadron_ssh_key")))).thenReturn(writeKeyResult);
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"chmod", "600", "/tmp/.squadron_ssh_key"}))).thenReturn(chmodResult);

        // Clone via SSH
        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(3000).build();
        when(workspaceProvider.exec(eq(containerId), argThat(cmd ->
                cmd.length == 3 && cmd[0].equals("sh") && cmd[1].equals("-c") && cmd[2].contains("GIT_SSH_COMMAND") && cmd[2].contains("clone")))).thenReturn(cloneResult);

        // SSH key cleanup
        ExecResult rmKeyResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(5).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}))).thenReturn(rmKeyResult);

        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----\n";
        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "git@github.com:test/repo.git", null, sshKey, null);

        assertTrue(result.isSuccess());
        assertEquals("Git repository is accessible", result.getMessage());

        // Verify SSH key cleanup and container destruction
        verify(workspaceProvider).exec(eq(containerId), eq(new String[]{"rm", "-f", "/tmp/.squadron_ssh_key"}));
        verify(workspaceProvider).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_failureNonZeroExitCode() {
        String containerId = "test-container-789";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        // git is installed
        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        // clone fails
        ExecResult cloneFailure = ExecResult.builder().exitCode(128).stdout("")
                .stderr("fatal: repository 'https://github.com/test/repo.git' not found").durationMs(2000).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"git", "clone", "--depth", "1", "--single-branch",
                "https://github.com/test/repo.git", "/tmp/test-clone"}))).thenReturn(cloneFailure);

        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "https://github.com/test/repo.git", null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Git clone failed"));
        assertTrue(result.getDurationMs() >= 0);

        // Verify container was still destroyed
        verify(workspaceProvider).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_cleanupOnException() {
        String containerId = "test-container-exc";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        // git check throws exception
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"})))
                .thenThrow(new RuntimeException("Container crashed"));

        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "https://github.com/test/repo.git", null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Git access test error"));

        // Verify container was still destroyed even on exception
        verify(workspaceProvider).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_containerDestroyedAfterTest() {
        String containerId = "test-container-destroy";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(1000).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"git", "clone", "--depth", "1", "--single-branch",
                "--branch", "develop", "https://github.com/test/repo.git", "/tmp/test-clone"}))).thenReturn(cloneResult);

        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "https://github.com/test/repo.git", null, null, "develop");

        assertTrue(result.isSuccess());
        assertEquals("develop", result.getBranch());

        // Verify container was destroyed exactly once
        verify(workspaceProvider, times(1)).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_withoutBranch() {
        String containerId = "test-container-nobranch";
        when(workspaceProvider.getProviderType()).thenReturn("KUBERNETES");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenReturn(containerId);

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(1000).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"git", "clone", "--depth", "1", "--single-branch",
                "https://github.com/test/repo.git", "/tmp/test-clone"}))).thenReturn(cloneResult);

        TestGitAccessResult result = workspaceGitService.testGitAccess(
                "https://github.com/test/repo.git", null, null, null);

        assertTrue(result.isSuccess());
        assertNull(result.getBranch());
        verify(workspaceProvider).destroyContainer(containerId);
    }

    @Test
    void should_testGitAccess_createsContainerWithCorrectSpec() {
        String containerId = "test-container-spec";
        when(workspaceProvider.getProviderType()).thenReturn("DOCKER");
        when(workspaceProvider.createContainer(any(WorkspaceSpec.class))).thenAnswer(invocation -> {
            WorkspaceSpec spec = invocation.getArgument(0);
            assertEquals("alpine:latest", spec.getBaseImage());
            assertEquals("64Mi", spec.getResourceLimits().get("memory"));
            assertEquals("DOCKER", spec.getProviderType());
            assertEquals("https://github.com/test/repo.git", spec.getRepoUrl());
            assertNotNull(spec.getTenantId());
            assertNotNull(spec.getTaskId());
            assertNotNull(spec.getUserId());
            return containerId;
        });

        ExecResult gitCheck = ExecResult.builder().exitCode(0).stdout("/usr/bin/git").stderr("").durationMs(10).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"which", "git"}))).thenReturn(gitCheck);

        ExecResult cloneResult = ExecResult.builder().exitCode(0).stdout("").stderr("").durationMs(1000).build();
        when(workspaceProvider.exec(eq(containerId), eq(new String[]{"git", "clone", "--depth", "1", "--single-branch",
                "https://github.com/test/repo.git", "/tmp/test-clone"}))).thenReturn(cloneResult);

        workspaceGitService.testGitAccess("https://github.com/test/repo.git", null, null, null);

        verify(workspaceProvider).createContainer(any(WorkspaceSpec.class));
        verify(workspaceProvider).destroyContainer(containerId);
    }
}
