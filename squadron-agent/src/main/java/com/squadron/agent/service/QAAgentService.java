package com.squadron.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.QAReportDto;
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
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the QA agent's agentic tool-calling loop.
 *
 * <p>When a task transitions to QA, the QA agent:
 * <ol>
 *   <li>Analyzes code changes against task requirements</li>
 *   <li>Runs tests in the workspace</li>
 *   <li>Collects coverage data</li>
 *   <li>Identifies test gaps</li>
 *   <li>Can generate missing tests via the tool loop</li>
 *   <li>Publishes an {@link AgentCompletedEvent} with the QA verdict</li>
 * </ol>
 */
@Service
public class QAAgentService {

    private static final Logger log = LoggerFactory.getLogger(QAAgentService.class);
    static final int MAX_ITERATIONS = 15;

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
    private final CoverageService coverageService;
    private final ObjectMapper objectMapper;

    public QAAgentService(ConversationService conversationService,
                          SquadronConfigService configService,
                          AgentProviderRegistry providerRegistry,
                          SystemPromptBuilder promptBuilder,
                          ToolRegistry toolRegistry,
                          ToolExecutionEngine toolExecutionEngine,
                          NatsEventPublisher natsEventPublisher,
                          CoverageService coverageService,
                          ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.coverageService = coverageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Main entry point: called when a task transitions to QA.
     * Runs the QA agentic loop, collects coverage, and publishes a completion event.
     */
    public void executeQA(TaskStateChangedEvent event) {
        UUID taskId = event.getTaskId();
        UUID tenantId = event.getTenantId();
        UUID userId = event.getTriggeredBy();

        log.info("Starting QA analysis for task {}", taskId);

        try {
            // 1. Start a conversation for the QA agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "QA");

            // 2. Resolve configuration for the QA agent type
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "QA");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 3. Build the QA system prompt with tool definitions
            String taskDescription = event.getReason() != null
                    ? event.getReason()
                    : "Perform QA analysis on the code changes for task " + taskId;
            String systemPrompt = buildQAPromptWithTools(
                    taskDescription, null, toolRegistry.getAllToolDefinitions());

            // 4. Run the agentic loop
            String initialMessage = "Please perform a thorough QA analysis of the code changes "
                    + "for task " + taskId + ". Use the provided tools to run tests, check coverage, "
                    + "read source files, and identify test gaps. If you find missing tests, "
                    + "generate them.\n\nTask: " + taskDescription;

            AgentLoopResult result = runQALoop(
                    conversation.getId(), tenantId, userId, config,
                    systemPrompt, initialMessage, taskId);

            // 5. Parse QA verdict from the response
            String verdict = parseQAVerdict(result.getSummary());

            // 6. Build QA report
            QAReportDto report = QAReportDto.builder()
                    .taskId(taskId)
                    .tenantId(tenantId)
                    .verdict(verdict)
                    .summary(result.getSummary())
                    .createdAt(Instant.now())
                    .build();

            boolean success = !"FAIL".equals(verdict);

            // 7. Publish completion event
            publishQACompletedEvent(tenantId, taskId, conversation.getId(),
                    success, result.getSummary());

            log.info("QA analysis {} for task {} with verdict {} after {} iterations",
                    success ? "completed" : "failed", taskId, verdict, result.getIterations());

        } catch (Exception e) {
            log.error("QA analysis failed for task {}", taskId, e);
            publishQACompletedEvent(tenantId, taskId, null, false,
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Runs the QA agentic tool-calling loop. The LLM responds with text or tool calls.
     * Tool calls are parsed from the response, executed, and results fed back.
     * Loop continues until the LLM signals completion or max iterations are reached.
     */
    AgentLoopResult runQALoop(UUID conversationId, UUID tenantId, UUID userId,
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
                // Ask the agent to continue or use tools
                currentMessage = "Please continue the QA analysis. Use the available tools "
                        + "to run tests, check coverage, and identify gaps. When you're done, "
                        + "include [DONE] in your response along with your QA verdict "
                        + "([QA_PASS], [QA_CONDITIONAL_PASS], or [QA_FAIL]).";
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
     * Builds a QA system prompt that includes tool definitions so the LLM
     * knows which tools are available and how to invoke them.
     *
     * @param taskDescription description of the task under test
     * @param diffContent     optional diff content showing code changes (may be null)
     * @param tools           available tool definitions
     * @return the complete system prompt
     */
    String buildQAPromptWithTools(String taskDescription, String diffContent,
                                  List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an expert QA engineer and testing agent for the Squadron platform.
                Your role is to thoroughly verify code changes, run tests, analyze coverage,
                identify test gaps, and generate missing tests when needed.
                
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
                ## Task Under Test
                
                """);
        sb.append(taskDescription).append("\n\n");

        if (diffContent != null && !diffContent.isEmpty()) {
            sb.append("""
                    ## Code Changes (Diff)
                    
                    ```diff
                    """);
            sb.append(diffContent);
            sb.append("\n```\n\n");
        }

        sb.append("""
                ## Instructions
                
                1. **Run existing tests** to verify they pass with the code changes.
                2. **Check test coverage** using the appropriate coverage tools.
                3. **Identify test gaps** — look for untested code paths, edge cases, error scenarios, and missing integration tests.
                4. **Generate missing tests** — write and save test files for any identified gaps.
                5. **Re-run tests** after generating new tests to confirm they pass.
                6. **Evaluate requirements coverage** — verify the code changes satisfy the task requirements.
                
                ## QA Verdict
                
                After completing your analysis, provide your verdict using one of these markers:
                - `[QA_PASS]` — All tests pass, coverage is adequate, requirements are met.
                - `[QA_CONDITIONAL_PASS]` — Tests pass but there are minor concerns (low coverage, edge cases not tested, etc.).
                - `[QA_FAIL]` — Tests fail, critical gaps exist, or requirements are not met.
                
                Include your overall assessment with findings in the following format:
                
                ```
                Overall QA verdict: PASS/CONDITIONAL_PASS/FAIL
                
                Findings:
                - [CATEGORY] STATUS: description
                
                Test Gaps:
                - description of gap
                
                Recommendations:
                - actionable recommendation
                ```
                
                When you are finished, include [DONE] in your response.
                """);

        return sb.toString();
    }

    /**
     * Parses the QA verdict from the agent's response. Looks for explicit markers
     * like [QA_PASS], [QA_CONDITIONAL_PASS], [QA_FAIL], or free-text patterns.
     * Defaults to CONDITIONAL_PASS if the verdict cannot be determined.
     *
     * @param response the agent's response text
     * @return one of "PASS", "CONDITIONAL_PASS", or "FAIL"
     */
    String parseQAVerdict(String response) {
        if (response == null || response.isEmpty()) {
            return "CONDITIONAL_PASS";
        }

        // Check for explicit markers first
        if (response.contains("[QA_FAIL]")) {
            return "FAIL";
        }
        if (response.contains("[QA_PASS]")) {
            return "PASS";
        }
        if (response.contains("[QA_CONDITIONAL_PASS]")) {
            return "CONDITIONAL_PASS";
        }

        // Check for free-text patterns (case-insensitive)
        String upper = response.toUpperCase();
        if (upper.contains("OVERALL QA VERDICT: FAIL") || upper.contains("QA VERDICT: FAIL")) {
            return "FAIL";
        }
        if (upper.contains("OVERALL QA VERDICT: PASS") || upper.contains("QA VERDICT: PASS")) {
            // Ensure it's not CONDITIONAL_PASS
            if (upper.contains("OVERALL QA VERDICT: CONDITIONAL_PASS")
                    || upper.contains("QA VERDICT: CONDITIONAL_PASS")) {
                return "CONDITIONAL_PASS";
            }
            return "PASS";
        }
        if (upper.contains("OVERALL QA VERDICT: CONDITIONAL_PASS")
                || upper.contains("QA VERDICT: CONDITIONAL_PASS")
                || upper.contains("CONDITIONAL PASS")) {
            return "CONDITIONAL_PASS";
        }

        // Default to CONDITIONAL_PASS if unclear
        return "CONDITIONAL_PASS";
    }

    /**
     * Publishes an {@link AgentCompletedEvent} with agentType="QA" to NATS.
     */
    void publishQACompletedEvent(UUID tenantId, UUID taskId, UUID conversationId,
                                  boolean success, String summary) {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setConversationId(conversationId);
        event.setAgentType("QA");
        event.setSuccess(success);
        event.setSource("squadron-agent");

        String subject = success
                ? "squadron.agent.qa.completed"
                : "squadron.agent.qa.failed";

        natsEventPublisher.publishAsync(subject, event);

        // Also publish to aggregated subject for notification service
        natsEventPublisher.publishAsync("squadron.agents.completed", event);

        log.info("Published QA {} event for task {} (summary: {})",
                success ? "completed" : "failed", taskId,
                summary != null && summary.length() > 100
                        ? summary.substring(0, 100) + "..." : summary);
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
}
