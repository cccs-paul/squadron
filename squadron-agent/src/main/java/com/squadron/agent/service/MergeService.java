package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates the auto-merge flow for pull requests.
 *
 * <p>When a task transitions to MERGE, this service:
 * <ol>
 *   <li>Looks up the pull request record for the task</li>
 *   <li>Checks the review gate (policy must be met)</li>
 *   <li>Checks if the PR is mergeable (no conflicts)</li>
 *   <li>Merges the pull request</li>
 *   <li>Publishes an {@link AgentCompletedEvent} on success or failure</li>
 * </ol>
 */
@Service
public class MergeService {

    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    private final GitClient gitClient;
    private final ReviewClient reviewClient;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public MergeService(GitClient gitClient,
                        ReviewClient reviewClient,
                        NatsEventPublisher natsEventPublisher,
                        ObjectMapper objectMapper) {
        this.gitClient = gitClient;
        this.reviewClient = reviewClient;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Main entry point: called when a task transitions to MERGE.
     * Checks review gate, mergeability, and performs the merge.
     */
    public void executeMerge(TaskStateChangedEvent event) {
        UUID taskId = event.getTaskId();
        UUID tenantId = event.getTenantId();

        log.info("Starting merge automation for task {}", taskId);

        try {
            // 1. Look up the PR for this task
            GitClient.PullRequestResponse pr = gitClient.getPullRequestByTaskId(taskId);
            String prId = pr.getId();

            // 2. Check review gate
            ReviewClient.ReviewGateResponse gate = reviewClient.checkReviewGate(taskId, tenantId, null);
            if (!gate.isPolicyMet()) {
                String reason = "Review gate not met for task " + taskId
                        + " (human approvals: " + gate.getHumanApprovals()
                        + ", AI approval: " + gate.isAiApproval() + ")";
                log.warn(reason);
                publishMergeEvent(tenantId, taskId, false, reason);
                return;
            }

            // 3. Check if PR is mergeable (no conflicts)
            GitClient.MergeabilityResponse mergeability = gitClient.checkMergeability(prId);
            if (!mergeability.isMergeable()) {
                String reason = "PR " + prId + " is not mergeable for task " + taskId
                        + " (conflict files: " + mergeability.getConflictFiles() + ")";
                log.warn(reason);
                publishMergeEvent(tenantId, taskId, false, reason);
                return;
            }

            // 4. Merge the PR using default MERGE strategy
            gitClient.mergePullRequest(prId, "MERGE");

            // 5. Publish success event
            log.info("Merge completed successfully for task {}", taskId);
            publishMergeEvent(tenantId, taskId, true, "Merge completed successfully");

        } catch (Exception e) {
            log.error("Merge automation failed for task {}", taskId, e);
            publishMergeEvent(tenantId, taskId, false, "Merge failed: " + e.getMessage());
        }
    }

    /**
     * Publishes an {@link AgentCompletedEvent} with agentType="MERGE" to NATS.
     */
    void publishMergeEvent(UUID tenantId, UUID taskId, boolean success, String summary) {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setAgentType("MERGE");
        event.setSuccess(success);
        event.setSource("squadron-agent");

        String subject = success
                ? "squadron.agent.merge.completed"
                : "squadron.agent.merge.failed";

        natsEventPublisher.publishAsync(subject, event);
        log.info("Published merge {} event for task {} (summary: {})",
                success ? "completed" : "failed", taskId,
                summary != null && summary.length() > 100
                        ? summary.substring(0, 100) + "..." : summary);
    }
}
