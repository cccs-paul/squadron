package com.squadron.agent.service;

import com.squadron.agent.dto.CodeGenerationRequest;
import com.squadron.agent.dto.CodeGenerationResult;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.config.NatsEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeGenerationServiceTest {

    @Mock
    private WorkspaceClient workspaceClient;

    @Mock
    private GitClient gitClient;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private CodeGenerationService codeGenerationService;

    private UUID tenantId;
    private UUID projectId;
    private UUID taskId;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        codeGenerationService = new CodeGenerationService(workspaceClient, gitClient, natsEventPublisher);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
    }

    private CodeGenerationRequest buildRequest() {
        return CodeGenerationRequest.builder()
                .workspaceId(workspaceId)
                .taskId(taskId)
                .tenantId(tenantId)
                .projectId(projectId)
                .taskTitle("Fix login bug")
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .accessToken("token")
                .commitMessage("fix: resolve login bug")
                .prTitle("Fix login bug")
                .prDescription("Resolves the login issue")
                .build();
    }

    private GitClient.BranchStrategyResponse buildStrategyResponse() {
        return GitClient.BranchStrategyResponse.builder()
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .build();
    }

    private ExecResultDto successExec() {
        return ExecResultDto.builder().exitCode(0).stdout("ok").stderr("").build();
    }

    private ExecResultDto failureExec(String stderr) {
        return ExecResultDto.builder().exitCode(1).stdout("").stderr(stderr).build();
    }

    @Test
    void should_generateAndCreatePr_successfully() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(UUID.randomUUID().toString())
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();
        when(gitClient.createPullRequest(any(GitClient.CreatePrRequest.class))).thenReturn(prResponse);

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertTrue(result.isSuccess());
        assertEquals(branchName, result.getBranchName());
        assertEquals("https://github.com/owner/repo/pull/42", result.getPrUrl());
        assertNotNull(result.getPrId());
        assertNull(result.getError());

        verify(gitClient).resolveStrategy(tenantId, projectId);
        verify(gitClient).generateBranchName(tenantId, projectId, taskId, "Fix login bug");
        verify(gitClient).createPullRequest(any(GitClient.CreatePrRequest.class));
        verify(natsEventPublisher).publish(eq("squadron.agent.coding.completed"), any());
    }

    @Test
    void should_handleBranchCreationFailure() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(failureExec("fatal: branch already exists"));

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertFalse(result.isSuccess());
        assertEquals(branchName, result.getBranchName());
        assertTrue(result.getError().contains("Branch creation failed"));
        assertNull(result.getPrUrl());

        verify(gitClient, never()).createPullRequest(any());
    }

    @Test
    void should_handleCommitFailure() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(failureExec("nothing to commit"));

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Commit failed"));

        verify(gitClient, never()).createPullRequest(any());
    }

    @Test
    void should_handlePushFailure() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(failureExec("remote rejected"));

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Push failed"));

        verify(gitClient, never()).createPullRequest(any());
    }

    @Test
    void should_handlePrCreationFailure() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());
        when(gitClient.createPullRequest(any(GitClient.CreatePrRequest.class)))
                .thenThrow(new GitClient.GitClientException("PR creation failed"));

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertFalse(result.isSuccess());
        assertEquals(branchName, result.getBranchName());
        assertTrue(result.getError().contains("PR creation failed"));
    }

    @Test
    void should_publishCompletionEvent() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(UUID.randomUUID().toString())
                .prNumber("10")
                .url("https://github.com/owner/repo/pull/10")
                .status("OPEN")
                .build();
        when(gitClient.createPullRequest(any())).thenReturn(prResponse);

        codeGenerationService.generateAndCreatePr(request);

        verify(natsEventPublisher).publish(eq("squadron.agent.coding.completed"), argThat(event ->
                "CODING_COMPLETED".equals(event.getEventType()) &&
                "squadron-agent".equals(event.getSource()) &&
                tenantId.equals(event.getTenantId())
        ));
    }

    @Test
    void should_useResolvedBranchStrategy() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "feature/abcd1234/fix-login-bug";

        GitClient.BranchStrategyResponse gitflowStrategy = GitClient.BranchStrategyResponse.builder()
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .build();

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(gitflowStrategy);
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(UUID.randomUUID().toString())
                .prNumber("5")
                .url("https://github.com/owner/repo/pull/5")
                .status("OPEN")
                .build();
        when(gitClient.createPullRequest(argThat(pr -> "develop".equals(pr.getTargetBranch()))))
                .thenReturn(prResponse);

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertTrue(result.isSuccess());
        assertEquals("feature/abcd1234/fix-login-bug", result.getBranchName());

        // Verify the PR was created with "develop" as target branch (from gitflow strategy)
        verify(gitClient).createPullRequest(argThat(pr -> "develop".equals(pr.getTargetBranch())));
    }

    @Test
    void should_handleWorkspaceExecFailure() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenThrow(new WorkspaceClient.WorkspaceClientException("Workspace unreachable"));

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Workspace unreachable"));
    }

    @Test
    void should_handleNatsPublishFailure_gracefully() {
        CodeGenerationRequest request = buildRequest();
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git add -A")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(UUID.randomUUID().toString())
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();
        when(gitClient.createPullRequest(any())).thenReturn(prResponse);
        doThrow(new RuntimeException("NATS down")).when(natsEventPublisher).publish(anyString(), any());

        // Should NOT throw even though NATS publish fails
        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertTrue(result.isSuccess());
        assertEquals(branchName, result.getBranchName());
    }

    @Test
    void should_escapeShellArg_correctly() {
        assertEquals("", codeGenerationService.escapeShellArg(null));
        assertEquals("hello", codeGenerationService.escapeShellArg("hello"));
        assertEquals("it'\\''s working", codeGenerationService.escapeShellArg("it's working"));
        assertEquals("no'\\''quotes'\\''here", codeGenerationService.escapeShellArg("no'quotes'here"));
    }

    @Test
    void should_useDefaultCommitMessage_when_noneProvided() {
        CodeGenerationRequest request = buildRequest();
        request.setCommitMessage(null); // No commit message provided
        String branchName = "squadron/abcd1234/fix-login-bug";

        when(gitClient.resolveStrategy(tenantId, projectId)).thenReturn(buildStrategyResponse());
        when(gitClient.generateBranchName(tenantId, projectId, taskId, "Fix login bug")).thenReturn(branchName);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git checkout -b")))
                .thenReturn(successExec());
        // Should use task title as default commit message
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("squadron: Fix login bug")))
                .thenReturn(successExec());
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), contains("git push origin")))
                .thenReturn(successExec());

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(UUID.randomUUID().toString())
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();
        when(gitClient.createPullRequest(any())).thenReturn(prResponse);

        CodeGenerationResult result = codeGenerationService.generateAndCreatePr(request);

        assertTrue(result.isSuccess());
    }
}
