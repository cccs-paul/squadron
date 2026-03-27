package com.squadron.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.TaskPlan;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the coding agent's agentic tool-calling loop.
 *
 * <p>When a task transitions to PROPOSE_CODE, the coding agent:
 * <ol>
 *   <li>Loads the approved plan from {@link PlanService}</li>
 *   <li>Starts a coding conversation via {@link ConversationService}</li>
 *   <li>Runs an agentic loop: send plan + context to LLM, parse tool calls,
 *       execute tools, feed results back, repeat until done</li>
 *   <li>Publishes an {@link AgentCompletedEvent} on completion</li>
 * </ol>
 */
@Service
public class CodingAgentService {

    private static final Logger log = LoggerFactory.getLogger(CodingAgentService.class);
    static final int MAX_ITERATIONS = 25;

    /** Regex to match: <tool_call name="tool_name">JSON_BODY</tool_call> */
    static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call\\s+name=\"([^\"]+)\">(.*?)</tool_call>",
            Pattern.DOTALL);

    private final PlanService planService;
    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionEngine toolExecutionEngine;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public CodingAgentService(PlanService planService,
                               ConversationService conversationService,
                               SquadronConfigService configService,
                               AgentProviderRegistry providerRegistry,
                               SystemPromptBuilder promptBuilder,
                               ToolRegistry toolRegistry,
                               ToolExecutionEngine toolExecutionEngine,
                               NatsEventPublisher natsEventPublisher,
                               ObjectMapper objectMapper) {
        this.planService = planService;
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Main entry point: called when a task transitions to PROPOSE_CODE.
     * Loads the approved plan, runs the agent loop, and publishes a completion event.
     */
    public void executeCodeGeneration(TaskStateChangedEvent event) {
        UUID taskId = event.getTaskId();
        UUID tenantId = event.getTenantId();
        UUID userId = event.getTriggeredBy();

        log.info("Starting code generation for task {}", taskId);

        try {
            // 1. Load the approved plan
            TaskPlan plan = planService.getLatestPlan(taskId);
            if (!"APPROVED".equals(plan.getStatus())) {
                log.warn("Latest plan for task {} is not approved (status={}), skipping",
                        taskId, plan.getStatus());
                return;
            }

            // 2. Start a conversation for the coding agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "CODING");

            // 3. Resolve configuration
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "CODING");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 4. Build the coding system prompt with tool definitions
            String systemPrompt = buildCodingPromptWithTools(
                    plan.getPlanContent(), toolRegistry.getAllToolDefinitions());

            // 5. Run the agentic loop
            String initialMessage = "Please implement the following plan. Use the provided tools "
                    + "to read files, write code, and run tests in the workspace.\n\nPlan:\n"
                    + plan.getPlanContent();

            AgentLoopResult result = runAgentLoop(
                    conversation.getId(), tenantId, userId, config,
                    systemPrompt, initialMessage, taskId);

            // 6. Publish completion event
            publishCodingCompletedEvent(tenantId, taskId, conversation.getId(),
                    result.isSuccess(), result.getSummary());

            log.info("Code generation {} for task {} after {} iterations",
                    result.isSuccess() ? "completed" : "failed", taskId, result.getIterations());

        } catch (Exception e) {
            log.error("Code generation failed for task {}", taskId, e);
            publishCodingCompletedEvent(tenantId, taskId, null, false,
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Runs the agentic tool-calling loop. The LLM responds with text or tool calls.
     * Tool calls are parsed from the response, executed, and results fed back.
     * Loop continues until the LLM signals completion or max iterations are reached.
     */
    AgentLoopResult runAgentLoop(UUID conversationId, UUID tenantId, UUID userId,
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
                currentMessage = "Please continue implementing the plan. Use the available "
                        + "tools to make code changes. When you're done, include [DONE] in "
                        + "your response.";
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
     * Builds a coding system prompt that includes tool definitions so the LLM
     * knows which tools are available and how to invoke them.
     */
    String buildCodingPromptWithTools(String planContent, List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an expert software engineer and coding agent for the Squadron platform.
                Your role is to implement code changes according to the provided plan.
                
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
                ## Implementation Plan
                
                """);
        sb.append(planContent).append("\n\n");

        sb.append("""
                ## Instructions
                1. Follow the plan step by step.
                2. Use the provided tools to read files, write code, and run commands.
                3. Write clean, well-documented code following project conventions.
                4. Include appropriate error handling and validation.
                5. When you have completed all changes, include [DONE] in your response along with a brief summary of what was implemented.
                """);

        return sb.toString();
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
     * Publishes an {@link AgentCompletedEvent} with agentType="CODING" to NATS.
     */
    void publishCodingCompletedEvent(UUID tenantId, UUID taskId, UUID conversationId,
                                      boolean success, String summary) {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setConversationId(conversationId);
        event.setAgentType("CODING");
        event.setSuccess(success);
        event.setSource("squadron-agent");

        String subject = success
                ? "squadron.agent.coding.completed"
                : "squadron.agent.coding.failed";

        natsEventPublisher.publishAsync(subject, event);

        // Also publish to aggregated subject for notification service
        natsEventPublisher.publishAsync("squadron.agents.completed", event);

        log.info("Published coding {} event for task {} (summary: {})",
                success ? "completed" : "failed", taskId,
                summary != null && summary.length() > 100
                        ? summary.substring(0, 100) + "..." : summary);
    }
}
