package com.squadron.git.service;

import com.squadron.common.security.TenantContext;
import com.squadron.git.dto.BranchRequest;
import com.squadron.git.dto.CloneRequest;
import com.squadron.git.dto.CommitRequest;
import com.squadron.git.dto.GitCommandResult;
import com.squadron.git.dto.PushRequest;
import com.squadron.git.entity.GitOperation;
import com.squadron.git.repository.GitOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service that wraps GitCliService calls with auditing to the git_operations table.
 */
@Service
@Transactional
public class GitOperationService {

    private static final Logger log = LoggerFactory.getLogger(GitOperationService.class);

    private final GitCliService gitCliService;
    private final GitOperationRepository gitOperationRepository;

    @Value("${squadron.workspace.base-path:/tmp/squadron/workspaces}")
    private String workspaceBasePath;

    public GitOperationService(GitCliService gitCliService,
                               GitOperationRepository gitOperationRepository) {
        this.gitCliService = gitCliService;
        this.gitOperationRepository = gitOperationRepository;
    }

    /**
     * Clone a repository into a workspace directory.
     */
    public GitCommandResult cloneRepo(UUID taskId, CloneRequest request) {
        String workDir = resolveWorkDir(request.getWorkspaceId());
        GitOperation operation = createOperation(taskId, request.getWorkspaceId(), "CLONE");

        GitCommandResult result = gitCliService.clone(
                request.getRepoUrl(), request.getBranch(), workDir, request.getAccessToken());

        completeOperation(operation, result);
        return result;
    }

    /**
     * Create a new branch in a workspace.
     */
    public GitCommandResult createBranch(UUID taskId, BranchRequest request) {
        String workDir = resolveWorkDir(request.getWorkspaceId());
        GitOperation operation = createOperation(taskId, request.getWorkspaceId(), "BRANCH");

        GitCommandResult result;
        if (request.getBaseBranch() != null && !request.getBaseBranch().isBlank()) {
            // First checkout the base branch, then create the new branch
            result = gitCliService.checkout(request.getBaseBranch(), workDir);
            if (result.isSuccess()) {
                result = gitCliService.createBranch(request.getBranchName(), workDir);
            }
        } else {
            result = gitCliService.createBranch(request.getBranchName(), workDir);
        }

        completeOperation(operation, result);
        return result;
    }

    /**
     * Commit changes in a workspace.
     */
    public GitCommandResult commit(UUID taskId, CommitRequest request) {
        String workDir = resolveWorkDir(request.getWorkspaceId());
        GitOperation operation = createOperation(taskId, request.getWorkspaceId(), "COMMIT");

        GitCommandResult result = gitCliService.commit(
                request.getMessage(), request.getAuthorName(), request.getAuthorEmail(), workDir);

        completeOperation(operation, result);
        return result;
    }

    /**
     * Push changes from a workspace to a remote.
     */
    public GitCommandResult push(UUID taskId, PushRequest request) {
        String workDir = resolveWorkDir(request.getWorkspaceId());
        GitOperation operation = createOperation(taskId, request.getWorkspaceId(), "PUSH");

        GitCommandResult result = gitCliService.push(
                request.getRemoteName(), request.getBranch(), workDir, request.getAccessToken());

        completeOperation(operation, result);
        return result;
    }

    /**
     * List operations for a task.
     */
    @Transactional(readOnly = true)
    public List<GitOperation> listOperationsByTask(UUID taskId) {
        return gitOperationRepository.findByTaskId(taskId);
    }

    private GitOperation createOperation(UUID taskId, UUID workspaceId, String operationType) {
        UUID tenantId = TenantContext.getTenantId();
        GitOperation operation = GitOperation.builder()
                .tenantId(tenantId != null ? tenantId : UUID.randomUUID())
                .taskId(taskId)
                .workspaceId(workspaceId)
                .operationType(operationType)
                .status("IN_PROGRESS")
                .build();
        return gitOperationRepository.save(operation);
    }

    private void completeOperation(GitOperation operation, GitCommandResult result) {
        if (result.isSuccess()) {
            operation.setStatus("COMPLETED");
        } else {
            operation.setStatus("FAILED");
            operation.setErrorMessage(result.getErrorOutput());
        }
        operation.setCompletedAt(Instant.now());
        gitOperationRepository.save(operation);
    }

    private String resolveWorkDir(UUID workspaceId) {
        return workspaceBasePath + "/" + workspaceId;
    }
}
