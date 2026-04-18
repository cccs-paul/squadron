package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewCommentRequest;
import com.squadron.agent.tool.builtin.ReviewBotClient;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the review agent's agentic tool-calling loop.
 *
 * <p>When a task transitions to REVIEW, the review agent:
 * <ol>
 *   <li>Retrieves the code diff from the workspace</li>
 *   <li>Creates an AI review record via {@link ReviewClient}</li>
 *   <li>Starts a review conversation via {@link ConversationService}</li>
 *   <li>Runs an agentic loop: send diff + context to LLM, parse tool calls,
 *       execute tools, feed results back, repeat until review is complete</li>
 *   <li>Parses structured review findings from the LLM response</li>
 *   <li>Submits the review with comments via {@link ReviewClient}</li>
 *   <li>Publishes an {@link AgentCompletedEvent} on completion</li>
 * </ol>
 */
@Service
public class ReviewAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgentService.class);
    static final int MAX_ITERATIONS = 10;

    private static final String NUDGE_MESSAGE =
            "Please finalize your review. If you need to inspect more files, "
                    + "use the available tools. When your review is complete, include [DONE] "
                    + "in your response along with your structured findings using the format:\n\n"
                    + "**Severity:** CRITICAL|MAJOR|MINOR|SUGGESTION\n"
                    + "**Location:** file.java:42\n"
                    + "**Category:** bug|security|performance|style|design\n"
                    + "**Issue:** Description of the issue\n"
                    + "**Suggestion:** How to fix it";

    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionEngine toolExecutionEngine;
    private final NatsEventPublisher natsEventPublisher;
    private final ReviewClient reviewClient;
    private final ReviewBotClient reviewBotClient;
    private final GitClient gitClient;
    private final WorkspaceClient workspaceClient;
    private final ObjectMapper objectMapper;
    private final WorkspaceLifecycleService workspaceLifecycleService;

    public ReviewAgentService(ConversationService conversationService,
                               SquadronConfigService configService,
                               AgentProviderRegistry providerRegistry,
                               SystemPromptBuilder promptBuilder,
                               ToolRegistry toolRegistry,
                               ToolExecutionEngine toolExecutionEngine,
                               NatsEventPublisher natsEventPublisher,
                               ReviewClient reviewClient,
                               ReviewBotClient reviewBotClient,
                               GitClient gitClient,
                               WorkspaceClient workspaceClient,
                               ObjectMapper objectMapper,
                               WorkspaceLifecycleService workspaceLifecycleService) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.reviewClient = reviewClient;
        this.reviewBotClient = reviewBotClient;
        this.gitClient = gitClient;
        this.workspaceClient = workspaceClient;
        this.objectMapper = objectMapper;
        this.workspaceLifecycleService = workspaceLifecycleService;
    }

    /**
     * Main entry point: called when a task transitions to REVIEW.
     * Retrieves the diff, runs the review agent loop, and submits findings.
     */
    public void executeReview(TaskStateChangedEvent event) {
        UUID taskId = event.getTaskId();
        UUID tenantId = event.getTenantId();
        UUID userId = event.getTriggeredBy();

        log.info("Starting AI review for task {}", taskId);

        try {
            // 1. Create an AI review record in the review service
            ReviewClient.ReviewResponse review = reviewClient.createReview(tenantId, taskId, "AI");
            UUID reviewId = review.getId();

            // 2. Find or provision workspace for the review
            WorkspaceInfo workspaceInfo = null;
            if (event.getTaskContext() != null) {
                try {
                    workspaceInfo = workspaceLifecycleService.findOrProvisionWorkspace(event.getTaskContext());
                    log.info("Using workspace {} for review of task {}",
                            workspaceInfo.getWorkspaceId(), taskId);
                } catch (Exception e) {
                    log.warn("Failed to find/provision workspace for review of task {}: {}", taskId, e.getMessage());
                }
            }

            // 3. Retrieve the diff from the workspace by executing git diff
            UUID diffTargetId = workspaceInfo != null ? workspaceInfo.getWorkspaceId() : taskId;
            String diffContent = retrieveDiff(diffTargetId);

            // 4. Start a conversation for the review agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "REVIEW");

            // 5. Resolve configuration for REVIEW agent
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "REVIEW");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 6. Build the review system prompt with tool definitions and diff
            String systemPrompt = buildReviewPromptWithTools(
                    diffContent, toolRegistry.getAllToolDefinitions());

            // 7. Run the agentic review loop
            String initialMessage = "Please review the following code changes. Examine the diff carefully, "
                    + "use the provided tools to read additional context files as needed, and provide "
                    + "a thorough code review with specific findings.\n\nDiff:\n```\n"
                    + diffContent + "\n```";

            AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                    conversation.getId(), tenantId, config,
                    systemPrompt, initialMessage, taskId, null,
                    MAX_ITERATIONS, NUDGE_MESSAGE, false,
                    conversationService, providerRegistry, toolExecutionEngine, objectMapper);

            // 8. Parse the review findings from the final response
            List<ReviewCommentRequest> comments = parseReviewFindings(result.getSummary());

            // 9. Determine review status based on findings
            String reviewStatus = determineReviewStatus(comments);

            // 10. Submit the review with comments
            reviewClient.submitReview(reviewId, reviewStatus, result.getSummary(), comments);

            // 11. Post review comments to git platform as bot (if configured)
            postReviewBotComments(event, taskId, tenantId, reviewStatus, comments, result.getSummary());

            // 12. Publish completion event
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, conversation.getId(),
                    "REVIEW", true, result.getSummary(), natsEventPublisher);

            log.info("AI review {} for task {} with status {} ({} comments, {} iterations)",
                    result.isSuccess() ? "completed" : "finished", taskId, reviewStatus,
                    comments.size(), result.getIterations());

        } catch (Exception e) {
            log.error("AI review failed for task {}", taskId, e);
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, null,
                    "REVIEW", false, "Error: " + e.getMessage(), natsEventPublisher);
        }
    }

    /**
     * Retrieves the code diff by executing {@code git diff main...HEAD} in the
     * task's workspace. Falls back to an empty diff if the workspace call fails.
     */
    String retrieveDiff(UUID taskId) {
        try {
            ExecResultDto result = workspaceClient.exec(taskId,
                    "bash", "-c", "git diff main...HEAD");
            if (result.getExitCode() == 0 && result.getStdout() != null
                    && !result.getStdout().isBlank()) {
                return result.getStdout();
            }
            log.warn("git diff returned exit code {} for task {}, trying HEAD~1",
                    result.getExitCode(), taskId);
            // Fallback: diff against previous commit
            ExecResultDto fallback = workspaceClient.exec(taskId,
                    "bash", "-c", "git diff HEAD~1");
            return fallback.getStdout() != null ? fallback.getStdout() : "";
        } catch (Exception e) {
            log.warn("Failed to retrieve diff for task {}: {}", taskId, e.getMessage());
            return "(diff unavailable)";
        }
    }

    /**
     * Builds a review system prompt that includes tool definitions and
     * instructions for performing a structured code review.
     */
    String buildReviewPromptWithTools(String diffContent, List<com.squadron.agent.tool.ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an expert code reviewer for the Squadron platform.
                Your role is to perform a thorough, constructive code review of the provided changes.
                
                ## Available Tools
                
                You can invoke tools using the following XML format:
                <tool_call name="tool_name">{"param": "value"}</tool_call>
                
                """);

        sb.append(AgentLoopSupport.renderToolDefinitions(tools));

        sb.append("""
                ## Review Guidelines
                
                1. **Correctness:** Check for bugs, logic errors, and edge cases.
                2. **Security:** Look for vulnerabilities, injection risks, and unsafe patterns.
                3. **Performance:** Identify inefficiencies, N+1 queries, and resource leaks.
                4. **Design:** Evaluate architecture, patterns, and SOLID principles.
                5. **Style:** Check naming conventions, formatting, and code clarity.
                
                ## Output Format
                
                For each finding, use this structured format:
                
                **Severity:** CRITICAL|MAJOR|MINOR|SUGGESTION
                **Location:** filename.java:lineNumber
                **Category:** bug|security|performance|style|design
                **Issue:** Description of the problem
                **Suggestion:** How to fix or improve it
                
                When your review is complete, include [DONE] followed by a brief summary.
                
                ## Code Changes to Review
                
                """);
        sb.append("```diff\n").append(diffContent).append("\n```\n");

        return sb.toString();
    }

    /**
     * Parses structured review findings from the LLM response. Looks for patterns:
     * {@code **Severity:** CRITICAL}, {@code **Location:** file.java:42}, etc.
     */
    List<ReviewCommentRequest> parseReviewFindings(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<ReviewCommentRequest> findings = new ArrayList<>();

        // Split on severity markers to find individual findings
        Pattern findingPattern = Pattern.compile(
                "\\*\\*Severity:\\*\\*\\s*(CRITICAL|MAJOR|MINOR|SUGGESTION)\\s*\n"
                        + "\\*\\*Location:\\*\\*\\s*([^\\n]+?)\\s*\n"
                        + "\\*\\*Category:\\*\\*\\s*([^\\n]+?)\\s*\n"
                        + "\\*\\*Issue:\\*\\*\\s*([^\\n]+(?:\\n(?!\\*\\*)[^\\n]+)*)\\s*\n"
                        + "(?:\\*\\*Suggestion:\\*\\*\\s*([^\\n]+(?:\\n(?!\\*\\*)[^\\n]+)*))?",
                Pattern.MULTILINE);

        Matcher matcher = findingPattern.matcher(response);
        while (matcher.find()) {
            String severity = matcher.group(1).trim();
            String location = matcher.group(2).trim();
            String category = matcher.group(3).trim();
            String issue = matcher.group(4).trim();
            String suggestion = matcher.group(5) != null ? matcher.group(5).trim() : "";

            // Parse location into filePath and lineNumber
            String filePath = location;
            Integer lineNumber = null;
            int colonIdx = location.lastIndexOf(':');
            if (colonIdx > 0) {
                try {
                    lineNumber = Integer.parseInt(location.substring(colonIdx + 1).trim());
                    filePath = location.substring(0, colonIdx).trim();
                } catch (NumberFormatException e) {
                    // Keep full location as filePath
                }
            }

            String body = issue;
            if (!suggestion.isEmpty()) {
                body = issue + "\n\nSuggestion: " + suggestion;
            }

            findings.add(ReviewCommentRequest.builder()
                    .filePath(filePath)
                    .lineNumber(lineNumber)
                    .body(body)
                    .severity(severity)
                    .category(category)
                    .build());
        }

        return findings;
    }

    /**
     * Determines the overall review status based on the severity of findings.
     * Returns "APPROVED" if there are no CRITICAL or MAJOR findings,
     * "CHANGES_REQUESTED" otherwise.
     */
    String determineReviewStatus(List<ReviewCommentRequest> comments) {
        if (comments == null || comments.isEmpty()) {
            return "APPROVED";
        }

        boolean hasBlockingFindings = comments.stream()
                .anyMatch(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity())
                        || "MAJOR".equalsIgnoreCase(c.getSeverity()));

        return hasBlockingFindings ? "CHANGES_REQUESTED" : "APPROVED";
    }

    /**
     * Posts review comments to the git platform as a bot user, if a review bot
     * is configured and enabled for the tenant's platform connection.
     */
    void postReviewBotComments(TaskStateChangedEvent event, UUID taskId, UUID tenantId,
                                String reviewStatus, List<ReviewCommentRequest> comments,
                                String summary) {
        try {
            // Get connectionId from task context
            UUID connectionId = null;
            if (event.getTaskContext() != null) {
                connectionId = event.getTaskContext().getConnectionId();
            }
            if (connectionId == null) {
                log.debug("No connectionId in task context for task {}; skipping bot comment", taskId);
                return;
            }

            // Look up enabled bot config
            java.util.Optional<ReviewBotClient.BotConfig> botConfigOpt =
                    reviewBotClient.getEnabledBotConfig(tenantId, connectionId);
            if (botConfigOpt.isEmpty()) {
                log.debug("No enabled review bot config for tenant {} connection {}; skipping", tenantId, connectionId);
                return;
            }

            ReviewBotClient.BotConfig botConfig = botConfigOpt.get();

            // Get the bot's access token
            String botToken = reviewBotClient.getBotAccessToken(botConfig.getId());

            // Find the PR for this task
            GitClient.PullRequestResponse pr = gitClient.getPullRequestByTaskId(taskId);

            // Format the review comment body
            String commentBody = formatBotReviewComment(reviewStatus, comments, summary);

            // Post the review comment
            gitClient.addPrReviewComment(pr.getId(), commentBody, botToken);

            // Auto-assign bot as reviewer if configured
            if (botConfig.isAutoAssign() && botConfig.getBotUsername() != null) {
                try {
                    gitClient.requestPrReviewers(pr.getId(),
                            java.util.List.of(botConfig.getBotUsername()), botToken);
                } catch (Exception e) {
                    log.warn("Failed to auto-assign bot reviewer {} for PR {}: {}",
                            botConfig.getBotUsername(), pr.getId(), e.getMessage());
                }
            }

            log.info("Review bot posted comment to PR {} for task {}", pr.getId(), taskId);

        } catch (Exception e) {
            log.warn("Failed to post review bot comment for task {}: {}", taskId, e.getMessage());
            // Non-fatal — the internal review is already saved
        }
    }

    /**
     * Formats review findings into a markdown comment body suitable for posting
     * on the git platform as a bot review comment.
     */
    String formatBotReviewComment(String reviewStatus, List<ReviewCommentRequest> comments, String summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Squadron AI Review\n\n");
        sb.append("**Status:** ").append(reviewStatus).append("\n\n");

        if (comments != null && !comments.isEmpty()) {
            sb.append("### Findings (").append(comments.size()).append(")\n\n");
            for (ReviewCommentRequest comment : comments) {
                sb.append("- **").append(comment.getSeverity()).append("** ");
                sb.append("`").append(comment.getFilePath());
                if (comment.getLineNumber() != null) {
                    sb.append(":").append(comment.getLineNumber());
                }
                sb.append("` — ").append(comment.getBody()).append("\n");
            }
            sb.append("\n");
        }

        if (summary != null && !summary.isBlank()) {
            sb.append("### Summary\n\n");
            sb.append(summary).append("\n");
        }

        return sb.toString();
    }
}
