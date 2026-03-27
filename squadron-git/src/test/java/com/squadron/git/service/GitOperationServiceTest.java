package com.squadron.git.service;

import com.squadron.git.dto.BranchRequest;
import com.squadron.git.dto.CloneRequest;
import com.squadron.git.dto.CommitRequest;
import com.squadron.git.dto.GitCommandResult;
import com.squadron.git.dto.PushRequest;
import com.squadron.git.entity.GitOperation;
import com.squadron.git.repository.GitOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitOperationServiceTest {

    @Mock
    private GitCliService gitCliService;

    @Mock
    private GitOperationRepository gitOperationRepository;

    private GitOperationService gitOperationService;

    @BeforeEach
    void setUp() {
        gitOperationService = new GitOperationService(gitCliService, gitOperationRepository);
        ReflectionTestUtils.setField(gitOperationService, "workspaceBasePath", "/tmp/test/workspaces");
    }

    @Test
    void should_cloneRepo_successfully() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("token")
                .build();

        GitCommandResult cmdResult = GitCommandResult.builder()
                .success(true)
                .output("Cloning into '.'...")
                .errorOutput("")
                .exitCode(0)
                .build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.clone(anyString(), anyString(), anyString(), anyString())).thenReturn(cmdResult);

        GitCommandResult result = gitOperationService.cloneRepo(taskId, request);

        assertTrue(result.isSuccess());
        verify(gitCliService).clone("https://github.com/owner/repo.git", "main",
                "/tmp/test/workspaces/" + workspaceId, "token");

        // Should save twice: once to create, once to complete
        verify(gitOperationRepository, times(2)).save(any(GitOperation.class));

        ArgumentCaptor<GitOperation> captor = ArgumentCaptor.forClass(GitOperation.class);
        verify(gitOperationRepository, times(2)).save(captor.capture());
        GitOperation savedOp = captor.getAllValues().get(1);
        assertEquals("COMPLETED", savedOp.getStatus());
        assertNotNull(savedOp.getCompletedAt());
    }

    @Test
    void should_cloneRepo_withFailure() {
        UUID taskId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(UUID.randomUUID())
                .repoUrl("https://github.com/owner/repo.git")
                .build();

        GitCommandResult cmdResult = GitCommandResult.builder()
                .success(false)
                .output("")
                .errorOutput("fatal: repository not found")
                .exitCode(128)
                .build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.clone(any(), any(), anyString(), any())).thenReturn(cmdResult);

        GitCommandResult result = gitOperationService.cloneRepo(taskId, request);

        assertFalse(result.isSuccess());

        ArgumentCaptor<GitOperation> captor = ArgumentCaptor.forClass(GitOperation.class);
        verify(gitOperationRepository, times(2)).save(captor.capture());
        GitOperation savedOp = captor.getAllValues().get(1);
        assertEquals("FAILED", savedOp.getStatus());
        assertEquals("fatal: repository not found", savedOp.getErrorMessage());
    }

    @Test
    void should_createBranch_withBaseBranch() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/new")
                .baseBranch("develop")
                .build();

        GitCommandResult checkoutResult = GitCommandResult.builder()
                .success(true).output("").errorOutput("").exitCode(0).build();
        GitCommandResult branchResult = GitCommandResult.builder()
                .success(true).output("Switched to new branch 'feature/new'").errorOutput("").exitCode(0).build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.checkout("develop", "/tmp/test/workspaces/" + workspaceId)).thenReturn(checkoutResult);
        when(gitCliService.createBranch("feature/new", "/tmp/test/workspaces/" + workspaceId)).thenReturn(branchResult);

        GitCommandResult result = gitOperationService.createBranch(taskId, request);

        assertTrue(result.isSuccess());
        verify(gitCliService).checkout("develop", "/tmp/test/workspaces/" + workspaceId);
        verify(gitCliService).createBranch("feature/new", "/tmp/test/workspaces/" + workspaceId);
    }

    @Test
    void should_createBranch_withoutBaseBranch() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/new")
                .build();

        GitCommandResult branchResult = GitCommandResult.builder()
                .success(true).output("Switched to new branch 'feature/new'").errorOutput("").exitCode(0).build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.createBranch("feature/new", "/tmp/test/workspaces/" + workspaceId)).thenReturn(branchResult);

        GitCommandResult result = gitOperationService.createBranch(taskId, request);

        assertTrue(result.isSuccess());
        verify(gitCliService, never()).checkout(anyString(), anyString());
        verify(gitCliService).createBranch("feature/new", "/tmp/test/workspaces/" + workspaceId);
    }

    @Test
    void should_commit_successfully() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug")
                .authorName("John Doe")
                .authorEmail("john@example.com")
                .build();

        GitCommandResult cmdResult = GitCommandResult.builder()
                .success(true).output("[main abc1234] Fix bug").errorOutput("").exitCode(0).build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.commit("Fix bug", "John Doe", "john@example.com",
                "/tmp/test/workspaces/" + workspaceId)).thenReturn(cmdResult);

        GitCommandResult result = gitOperationService.commit(taskId, request);

        assertTrue(result.isSuccess());
    }

    @Test
    void should_push_successfully() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .branch("main")
                .accessToken("token")
                .build();

        GitCommandResult cmdResult = GitCommandResult.builder()
                .success(true).output("Everything up-to-date").errorOutput("").exitCode(0).build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.push("origin", "main", "/tmp/test/workspaces/" + workspaceId, "token"))
                .thenReturn(cmdResult);

        GitCommandResult result = gitOperationService.push(taskId, request);

        assertTrue(result.isSuccess());
    }

    @Test
    void should_listOperationsByTask() {
        UUID taskId = UUID.randomUUID();
        GitOperation op1 = GitOperation.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .operationType("CLONE")
                .status("COMPLETED")
                .build();
        GitOperation op2 = GitOperation.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .operationType("PUSH")
                .status("FAILED")
                .build();

        when(gitOperationRepository.findByTaskId(taskId)).thenReturn(List.of(op1, op2));

        List<GitOperation> results = gitOperationService.listOperationsByTask(taskId);

        assertEquals(2, results.size());
    }

    @Test
    void should_createBranch_failWhenBaseCheckoutFails() {
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/new")
                .baseBranch("nonexistent")
                .build();

        GitCommandResult checkoutResult = GitCommandResult.builder()
                .success(false).output("").errorOutput("error: pathspec 'nonexistent' did not match").exitCode(1).build();

        when(gitOperationRepository.save(any(GitOperation.class))).thenAnswer(invocation -> {
            GitOperation op = invocation.getArgument(0);
            op.setId(UUID.randomUUID());
            return op;
        });
        when(gitCliService.checkout("nonexistent", "/tmp/test/workspaces/" + workspaceId)).thenReturn(checkoutResult);

        GitCommandResult result = gitOperationService.createBranch(taskId, request);

        assertFalse(result.isSuccess());
        verify(gitCliService, never()).createBranch(anyString(), anyString());
    }
}
