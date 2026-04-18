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
import com.squadron.agent.tool.ToolResult;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared utility methods for the agent tool-calling loop, used by
 * {@link CodingAgentService}, {@link ReviewAgentService}, and {@link QAAgentService}.
 *
 * <p>Centralizes: tool call parsing, completion detection, summary extraction,
 * tool result formatting, output sanitization, tool definition rendering,
 * agent loop execution, and event publishing.
 */
public final class AgentLoopSupport {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopSupport.class);

    /** Regex to match: {@code <tool_call name="tool_name">JSON_BODY</tool_call>} */
    static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call\\s+name=\"([^\"]+)\">(.*?)</tool_call>",
            Pattern.DOTALL);

    private AgentLoopSupport() {
        // utility class
    }

    // ── Tool call parsing ──────────────────────────────────────────────

    /**
     * Parses tool calls from the LLM response text.
     */
    static List<ToolCall> parseToolCalls(String response, ObjectMapper objectMapper) {
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

    // ── Completion detection ───────────────────────────────────────────

    /**
     * Returns {@code true} if the response contains a completion signal.
     */
    static boolean isCompletionSignal(String response) {
        if (response == null) {
            return false;
        }
        return response.contains("[DONE]") || response.contains("[COMPLETE]");
    }

    // ── Summary extraction ─────────────────────────────────────────────

    /**
     * Extracts a summary from the completion response, looking for text after
     * the completion marker and falling back to a truncated response.
     */
    static String extractSummary(String response) {
        if (response == null || response.isEmpty()) {
            return "No summary provided";
        }

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

        return response.length() > 500 ? response.substring(0, 500) : response;
    }

    // ── Tool result formatting ─────────────────────────────────────────

    /**
     * Formats tool execution results into a human-readable string for the LLM.
     *
     * @param results    the tool results
     * @param sanitize   if {@code true}, credential patterns are scrubbed from output
     */
    static String formatToolResults(List<ToolResult> results, boolean sanitize) {
        if (results == null || results.isEmpty()) {
            return "No tool results.";
        }

        return results.stream()
                .map(result -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("## Tool: ").append(result.getToolName()).append("\n");
                    sb.append("Status: ").append(result.isSuccess() ? "SUCCESS" : "FAILED").append("\n");
                    if (result.isSuccess() && result.getOutput() != null) {
                        String output = sanitize ? sanitizeOutput(result.getOutput()) : result.getOutput();
                        sb.append("Output:\n```\n").append(output).append("\n```\n");
                    }
                    if (!result.isSuccess() && result.getError() != null) {
                        String error = sanitize ? sanitizeOutput(result.getError()) : result.getError();
                        sb.append("Error: ").append(error).append("\n");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Sanitizes output to remove embedded credentials/tokens from URLs.
     */
    static String sanitizeOutput(String output) {
        if (output == null) return "";
        return output.replaceAll("(https?://)[^@/]+@", "$1***@")
                .replaceAll("(?<!https?://)oauth2:[^@]+@", "oauth2:***@");
    }

    // ── Tool definition rendering ──────────────────────────────────────

    /**
     * Renders a list of tool definitions into markdown for inclusion in system prompts.
     */
    static String renderToolDefinitions(List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    // ── Agent loop execution ───────────────────────────────────────────

    /**
     * Runs the generic agentic tool-calling loop shared by all agent types.
     *
     * @param conversationId    the conversation to record messages in
     * @param tenantId          tenant scope
     * @param config            agent configuration (provider, model, etc.)
     * @param systemPrompt      the system prompt including tool definitions
     * @param initialMessage    first user message to the LLM
     * @param taskId            task identifier for tool execution context
     * @param workspaceId       workspace identifier (may be null)
     * @param maxIterations     maximum loop iterations
     * @param nudgeMessage      message sent when the LLM responds without tool calls or completion
     * @param sanitizeOutput    whether to sanitize credentials in tool output
     * @param conversationService  conversation service
     * @param providerRegistry     provider registry
     * @param toolExecutionEngine  tool execution engine
     * @param objectMapper         JSON mapper
     */
    static AgentLoopResult runAgentLoop(UUID conversationId, UUID tenantId,
                                        AgentConfigDto config, String systemPrompt,
                                        String initialMessage, UUID taskId,
                                        UUID workspaceId, int maxIterations,
                                        String nudgeMessage, boolean sanitizeOutput,
                                        ConversationService conversationService,
                                        AgentProviderRegistry providerRegistry,
                                        ToolExecutionEngine toolExecutionEngine,
                                        ObjectMapper objectMapper) {
        AgentProvider provider = providerRegistry.getProvider(
                config.getProvider() != null ? config.getProvider() : "openai-compatible");

        List<ChatMessage> history = new ArrayList<>();
        String currentMessage = initialMessage;
        int iterations = 0;

        while (iterations < maxIterations) {
            iterations++;

            conversationService.addMessage(conversationId,
                    iterations == 1 ? "USER" : "SYSTEM", currentMessage, null);

            String response;
            try {
                response = provider.chat(systemPrompt, history, currentMessage, config);
            } catch (Exception e) {
                log.error("LLM call failed at iteration {}", iterations, e);
                return new AgentLoopResult(false, iterations,
                        "LLM call failed: " + e.getMessage());
            }

            conversationService.addMessage(conversationId, "ASSISTANT", response,
                    response.length() / 4);

            history.add(ChatMessage.builder().role("USER").content(currentMessage).build());
            history.add(ChatMessage.builder().role("ASSISTANT").content(response).build());

            List<ToolCall> toolCalls = parseToolCalls(response, objectMapper);

            if (toolCalls.isEmpty()) {
                if (isCompletionSignal(response)) {
                    return new AgentLoopResult(true, iterations, extractSummary(response));
                }
                currentMessage = nudgeMessage;
                continue;
            }

            ToolExecutionContext baseContext = ToolExecutionContext.builder()
                    .taskId(taskId)
                    .tenantId(tenantId)
                    .workspaceId(workspaceId)
                    .build();

            List<ToolResult> results = toolExecutionEngine.executeTools(toolCalls, baseContext);
            currentMessage = formatToolResults(results, sanitizeOutput);
        }

        return new AgentLoopResult(false, iterations, "Max iterations reached");
    }

    // ── Event publishing ───────────────────────────────────────────────

    /**
     * Publishes an {@link AgentCompletedEvent} with the given agent type to NATS.
     */
    static void publishCompletedEvent(UUID tenantId, UUID taskId, UUID conversationId,
                                       String agentType, boolean success, String summary,
                                       NatsEventPublisher natsEventPublisher) {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setConversationId(conversationId);
        event.setAgentType(agentType);
        event.setSuccess(success);
        event.setSource("squadron-agent");

        String agentLower = agentType.toLowerCase();
        String subject = success
                ? "squadron.agent." + agentLower + ".completed"
                : "squadron.agent." + agentLower + ".failed";

        natsEventPublisher.publishAsync(subject, event);
        natsEventPublisher.publishAsync("squadron.agents.completed", event);

        log.info("Published {} {} event for task {} (summary: {})",
                agentLower, success ? "completed" : "failed", taskId,
                summary != null && summary.length() > 100
                        ? summary.substring(0, 100) + "..." : summary);
    }
}
