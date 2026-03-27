package com.squadron.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import com.squadron.agent.tool.ToolCall;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.agent.tool.ToolResult;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewCommentRequest;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /** Regex to match: <tool_call name="tool_name">JSON_BODY</tool_call> */
    static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call\\s+name=\"([^\"]+)\">(.*?)</tool_call>",
            Pattern.DOTALL);

    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionEngine toolExecutionEngine;
    private final NatsEventPublisher natsEventPublisher;
    private final ReviewClient reviewClient;
    private final WorkspaceClient workspaceClient;
    private final ObjectMapper objectMapper;

    public ReviewAgentService(ConversationService conversationService,
                               SquadronConfigService configService,
                               AgentProviderRegistry providerRegistry,
                               SystemPromptBuilder promptBuilder,
                               ToolRegistry toolRegistry,
                               ToolExecutionEngine toolExecutionEngine,
                               NatsEventPublisher natsEventPublisher,
                               ReviewClient reviewClient,
                               WorkspaceClient workspaceClient,
                               ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.reviewClient = reviewClient;
        this.workspaceClient = workspaceClient;
        this.objectMapper = objectMapper;
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

            // 2. Retrieve the diff from the workspace by executing git diff
            String diffContent = retrieveDiff(taskId);

            // 3. Start a conversation for the review agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "REVIEW");

            // 4. Resolve configuration for REVIEW agent
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "REVIEW");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 5. Build the review system prompt with tool definitions and diff
            String systemPrompt = buildReviewPromptWithTools(
                    diffContent, toolRegistry.getAllToolDefinitions());

            // 6. Run the agentic review loop
            String initialMessage = "Please review the following code changes. Examine the diff carefully, "
                    + "use the provided tools to read additional context files as needed, and provide "
                    + "a thorough code review with specific findings.\n\nDiff:\n```\n"
                    + diffContent + "\n```";

            AgentLoopResult result = runReviewLoop(
                    conversation.getId(), tenantId, userId, config,
                    systemPrompt, initialMessage, taskId);

            // 7. Parse the review findings from the final response
            List<ReviewCommentRequest> comments = parseReviewFindings(result.getSummary());

            // 8. Determine review status based on findings
            String reviewStatus = determineReviewStatus(comments);

            // 9. Submit the review with comments
            reviewClient.submitReview(reviewId, reviewStatus, result.getSummary(), comments);

            // 10. Publish completion event
            publishReviewCompletedEvent(tenantId, taskId, conversation.getId(),
                    true, result.getSummary());

            log.info("AI review {} for task {} with status {} ({} comments, {} iterations)",
                    result.isSuccess() ? "completed" : "finished", taskId, reviewStatus,
                    comments.size(), result.getIterations());

        } catch (Exception e) {
            log.error("AI review failed for task {}", taskId, e);
            publishReviewCompletedEvent(tenantId, taskId, null, false,
                    "Error: " + e.getMessage());
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
     * Runs the agentic tool-calling loop for the review agent. The LLM responds
     * with text or tool calls. Tool calls are parsed, executed, and results fed back.
     * Loop continues until the LLM signals completion or max iterations are reached.
     */
    AgentLoopResult runReviewLoop(UUID conversationId, UUID tenantId, UUID userId,
                                   AgentConfigDto config, String systemPrompt,
                                   String initialMessage, UUID taskId) {
        AgentProvider provider = providerRegistry.getProvider(
                config.getProvider() != null ? config.getProvider() : "openai-compatible");

        List<ChatMessage> history = new ArrayList<>();
        String currentMessage = initialMessage;
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;

            // Save user/system message
            conversationService.addMessage(conversationId,
                    iterations == 1 ? "USER" : "SYSTEM", currentMessage, null);

            // Call LLM
            String response;
            try {
                response = provider.chat(systemPrompt, history, currentMessage, config);
            } catch (Exception e) {
                log.error("LLM call failed at iteration {}", iterations, e);
                return new AgentLoopResult(false, iterations,
                        "LLM call failed: " + e.getMessage());
            }

            // Save assistant response
            conversationService.addMessage(conversationId, "ASSISTANT", response,
                    response.length() / 4);

            // Add to history
            history.add(ChatMessage.builder().role("USER").content(currentMessage).build());
            history.add(ChatMessage.builder().role("ASSISTANT").content(response).build());

            // Parse tool calls from response
            List<ToolCall> toolCalls = parseToolCalls(response);

            if (toolCalls.isEmpty()) {
                // No tool calls — agent is done (or just conversing)
                if (isCompletionSignal(response)) {
                    return new AgentLoopResult(true, iterations, extractSummary(response));
                }
                // Ask the agent to continue or finalize
                currentMessage = "Please finalize your review. If you need to inspect more files, "
                        + "use the available tools. When your review is complete, include [DONE] "
                        + "in your response along with your structured findings using the format:\n\n"
                        + "**Severity:** CRITICAL|MAJOR|MINOR|SUGGESTION\n"
                        + "**Location:** file.java:42\n"
                        + "**Category:** bug|security|performance|style|design\n"
                        + "**Issue:** Description of the issue\n"
                        + "**Suggestion:** How to fix it";
                continue;
            }

            // Execute tool calls
            ToolExecutionContext baseContext = ToolExecutionContext.builder()
                    .taskId(taskId)
                    .tenantId(tenantId)
                    .build();

            List<ToolResult> results = toolExecutionEngine.executeTools(toolCalls, baseContext);

            // Format tool results as the next message
            currentMessage = formatToolResults(results);
        }

        return new AgentLoopResult(false, iterations, "Max iterations reached");
    }

    /**
     * Builds a review system prompt that includes tool definitions and
     * instructions for performing a structured code review.
     *
     * @param diffContent the code diff to review
     * @param tools       available tool definitions
     * @return the assembled system prompt
     */
    String buildReviewPromptWithTools(String diffContent, List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an expert code reviewer for the Squadron platform.
                Your role is to perform a thorough, constructive code review of the provided changes.
                
                ## Available Tools
                
                You can invoke tools using the following XML format:
                <tool_call name="tool_name">{"param": "value"}</tool_call>
                
                """);

        for (ToolDefinition tool : tools) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append(tool.getDescription()).append("\n");
            if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                sb.append("Parameters:\n");
                for (ToolParameter param : tool.getParameters()) {
                    sb.append("  - `").append(param.getName()).append("` (")
                            .append(param.getType()).append(")")
                            .append(param.isRequired() ? " **required**" : " optional")
                            .append(": ").append(param.getDescription()).append("\n");
                }
            }
            sb.append("\n");
        }

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
     *
     * @param comments the parsed review comments
     * @return "APPROVED" or "CHANGES_REQUESTED"
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
     * Parses tool calls from the LLM response text. The LLM is instructed to use
     * the format: {@code <tool_call name="tool_name">{"param": "value"}</tool_call>}
     */
    List<ToolCall> parseToolCalls(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(response);

        while (matcher.find()) {
            String toolName = matcher.group(1);
            String jsonBody = matcher.group(2).trim();

            try {
                Map<String, Object> arguments = objectMapper.readValue(
                        jsonBody, new TypeReference<>() {});
                toolCalls.add(ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(toolName)
                        .arguments(arguments)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to parse tool call arguments for tool '{}': {}",
                        toolName, e.getMessage());
            }
        }

        return toolCalls;
    }

    /**
     * Returns {@code true} if the response contains a completion signal.
     */
    boolean isCompletionSignal(String response) {
        if (response == null) {
            return false;
        }
        return response.contains("[DONE]") || response.contains("[COMPLETE]");
    }

    /**
     * Extracts a summary from the completion response. Looks for text after
     * the completion marker, falling back to the last paragraph.
     */
    String extractSummary(String response) {
        if (response == null || response.isEmpty()) {
            return "No summary provided";
        }

        // Try to extract text after [DONE] or [COMPLETE]
        int doneIdx = response.indexOf("[DONE]");
        if (doneIdx >= 0) {
            String after = response.substring(doneIdx + "[DONE]".length()).trim();
            if (!after.isEmpty()) {
                return after.length() > 500 ? after.substring(0, 500) : after;
            }
        }

        int completeIdx = response.indexOf("[COMPLETE]");
        if (completeIdx >= 0) {
            String after = response.substring(completeIdx + "[COMPLETE]".length()).trim();
            if (!after.isEmpty()) {
                return after.length() > 500 ? after.substring(0, 500) : after;
            }
        }

        // Fall back to the response itself (truncated)
        return response.length() > 500 ? response.substring(0, 500) : response;
    }

    /**
     * Formats tool execution results into a human-readable string that will be
     * sent back to the LLM as the next message in the conversation.
     */
    String formatToolResults(List<ToolResult> results) {
        if (results == null || results.isEmpty()) {
            return "No tool results.";
        }

        return results.stream()
                .map(result -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("## Tool: ").append(result.getToolName()).append("\n");
                    sb.append("Status: ").append(result.isSuccess() ? "SUCCESS" : "FAILED").append("\n");
                    if (result.isSuccess() && result.getOutput() != null) {
                        sb.append("Output:\n```\n").append(result.getOutput()).append("\n```\n");
                    }
                    if (!result.isSuccess() && result.getError() != null) {
                        sb.append("Error: ").append(result.getError()).append("\n");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Publishes an {@link AgentCompletedEvent} with agentType="REVIEW" to NATS.
     */
    void publishReviewCompletedEvent(UUID tenantId, UUID taskId, UUID conversationId,
                                      boolean success, String summary) {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setConversationId(conversationId);
        event.setAgentType("REVIEW");
        event.setSuccess(success);
        event.setSource("squadron-agent");

        String subject = success
                ? "squadron.agent.review.completed"
                : "squadron.agent.review.failed";

        natsEventPublisher.publishAsync(subject, event);
        log.info("Published review {} event for task {} (summary: {})",
                success ? "completed" : "failed", taskId,
                summary != null && summary.length() > 100
                        ? summary.substring(0, 100) + "..." : summary);
    }
}
