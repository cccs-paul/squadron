package com.squadron.agent.client;

import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resilient wrapper around {@link GitServiceClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Slf4j
@Service
public class ResilientGitServiceClient {

    private final GitServiceClient delegate;
    private final ResilientClient resilientClient;

    @Autowired
    public ResilientGitServiceClient(GitServiceClient delegate) {
        this.delegate = delegate;
        this.resilientClient = ResilientClient.withDefaults("git-service");
        log.info("Initialized resilient git service client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    ResilientGitServiceClient(GitServiceClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    public Map<String, Object> resolveStrategy(String tenantId, String projectId) {
        return resilientClient.execute("resolveStrategy",
                () -> delegate.resolveStrategy(tenantId, projectId));
    }

    public Map<String, Object> generateBranchName(String tenantId, String taskId,
                                                    String taskTitle, String projectId) {
        return resilientClient.execute("generateBranchName",
                () -> delegate.generateBranchName(tenantId, taskId, taskTitle, projectId));
    }

    public Map<String, Object> createPullRequest(Map<String, Object> request) {
        return resilientClient.execute("createPullRequest",
                () -> delegate.createPullRequest(request));
    }

    public Map<String, Object> getPullRequestByTaskId(String taskId) {
        return resilientClient.execute("getPullRequestByTaskId",
                () -> delegate.getPullRequestByTaskId(taskId));
    }

    public Map<String, Object> checkMergeability(String id) {
        return resilientClient.execute("checkMergeability",
                () -> delegate.checkMergeability(id));
    }

    public Map<String, Object> mergePullRequest(String id) {
        return resilientClient.execute("mergePullRequest",
                () -> delegate.mergePullRequest(id));
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
