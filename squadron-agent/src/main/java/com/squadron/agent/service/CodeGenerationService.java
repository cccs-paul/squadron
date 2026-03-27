package com.squadron.agent.service;

import com.squadron.agent.dto.CodeGenerationRequest;
import com.squadron.agent.dto.CodeGenerationResult;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates the code generation and PR creation workflow.
 * After the coding agent finishes its tool-calling loop, this service:
 * 1. Resolves the branch strategy from squadron-git
 * 2. Generates a branch name
 * 3. Creates a branch in the workspace container
 * 4. Commits all changes
 * 5. Pushes the branch
 * 6. Creates a PR via squadron-git REST API
 * 7. Publishes a completion event
 */
@Service
public class CodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationService.class);

    private final WorkspaceClient workspaceClient;
    private final GitClient gitClient;
    private final NatsEventPublisher natsEventPublisher;

    public CodeGenerationService(WorkspaceClient workspaceClient,
                                  GitClient gitClient,
                                  NatsEventPublisher natsEventPublisher) {
        this.workspaceClient = workspaceClient;
        this.gitClient = gitClient;
        this.natsEventPublisher = natsEventPublisher;
    }

    /**
     * Main orchestration method: generates a branch, commits changes, pushes,
     * creates a PR, and publishes a completion event.
     */
    public CodeGenerationResult generateAndCreatePr(CodeGenerationRequest request) {
        log.info("Starting code generation workflow for task {} in workspace {}",
                request.getTaskId(), request.getWorkspaceId());

        String branchName = null;

        try {
            // 1. Resolve branch strategy
            GitClient.BranchStrategyResponse strategy = gitClient.resolveStrategy(
                    request.getTenantId(), request.getProjectId());
            log.debug("Resolved branch strategy: type={}, target={}", strategy.getStrategyType(), strategy.getTargetBranch());

            // 2. Generate branch name
            branchName = gitClient.generateBranchName(
                    request.getTenantId(), request.getProjectId(),
                    request.getTaskId(), request.getTaskTitle());
            log.info("Generated branch name: {}", branchName);

            // 3. Create branch in workspace
            ExecResultDto branchResult = workspaceClient.exec(request.getWorkspaceId(),
                    "bash", "-c", "git checkout -b " + branchName);
            if (branchResult.getExitCode() != 0) {
                return failureResult(branchName, "Branch creation failed: " + branchResult.getStderr());
            }
            log.info("Branch {} created in workspace {}", branchName, request.getWorkspaceId());

            // 4. Commit all changes
            String commitMessage = request.getCommitMessage() != null
                    ? request.getCommitMessage()
                    : "squadron: " + request.getTaskTitle();
            ExecResultDto commitResult = workspaceClient.exec(request.getWorkspaceId(),
                    "bash", "-c", "git add -A && git commit -m '" + escapeShellArg(commitMessage) + "'");
            if (commitResult.getExitCode() != 0) {
                return failureResult(branchName, "Commit failed: " + commitResult.getStderr());
            }
            log.info("Changes committed in workspace {}", request.getWorkspaceId());

            // 5. Push branch
            ExecResultDto pushResult = workspaceClient.exec(request.getWorkspaceId(),
                    "bash", "-c", "git push origin " + branchName);
            if (pushResult.getExitCode() != 0) {
                return failureResult(branchName, "Push failed: " + pushResult.getStderr());
            }
            log.info("Branch {} pushed in workspace {}", branchName, request.getWorkspaceId());

            // 6. Create PR
            String targetBranch = strategy.getTargetBranch() != null ? strategy.getTargetBranch() : "main";
            GitClient.CreatePrRequest prRequest = GitClient.CreatePrRequest.builder()
                    .tenantId(request.getTenantId())
                    .taskId(request.getTaskId())
                    .platform(request.getPlatform())
                    .repoOwner(request.getRepoOwner())
                    .repoName(request.getRepoName())
                    .title(request.getPrTitle() != null ? request.getPrTitle() : request.getTaskTitle())
                    .description(request.getPrDescription())
                    .sourceBranch(branchName)
                    .targetBranch(targetBranch)
                    .accessToken(request.getAccessToken())
                    .build();

            GitClient.PullRequestResponse prResponse = gitClient.createPullRequest(prRequest);
            log.info("PR created: {} ({})", prResponse.getUrl(), prResponse.getId());

            // 7. Publish completion event
            publishCompletionEvent(request.getTenantId(), request.getTaskId(), branchName, prResponse.getUrl());

            return CodeGenerationResult.builder()
                    .success(true)
                    .branchName(branchName)
                    .prUrl(prResponse.getUrl())
                    .prId(prResponse.getId())
                    .build();

        } catch (Exception e) {
            log.error("Code generation workflow failed for task {}: {}", request.getTaskId(), e.getMessage(), e);
            return failureResult(branchName, e.getMessage());
        }
    }

    private CodeGenerationResult failureResult(String branchName, String error) {
        return CodeGenerationResult.builder()
                .success(false)
                .branchName(branchName)
                .error(error)
                .build();
    }

    private void publishCompletionEvent(UUID tenantId, UUID taskId, String branchName, String prUrl) {
        try {
            SquadronEvent event = new SquadronEvent();
            event.setEventType("CODING_COMPLETED");
            event.setTenantId(tenantId);
            event.setSource("squadron-agent");
            natsEventPublisher.publish("squadron.agent.coding.completed", event);
            log.info("Published coding completion event for task {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to publish coding completion event for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * Escape single quotes in shell arguments.
     */
    String escapeShellArg(String arg) {
        if (arg == null) {
            return "";
        }
        return arg.replace("'", "'\\''");
    }
}
