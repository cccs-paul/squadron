package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    private static final String NUDGE_MESSAGE =
            "Please continue implementing the plan. Use the available "
                    + "tools to make code changes. When you're done, include [DONE] in "
                    + "your response.";

    private final PlanService planService;
    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionEngine toolExecutionEngine;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;
    private final WorkspaceLifecycleService workspaceLifecycleService;

    public CodingAgentService(PlanService planService,
                               ConversationService conversationService,
                               SquadronConfigService configService,
                               AgentProviderRegistry providerRegistry,
                               SystemPromptBuilder promptBuilder,
                               ToolRegistry toolRegistry,
                               ToolExecutionEngine toolExecutionEngine,
                               NatsEventPublisher natsEventPublisher,
                               ObjectMapper objectMapper,
                               WorkspaceLifecycleService workspaceLifecycleService) {
        this.planService = planService;
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
        this.workspaceLifecycleService = workspaceLifecycleService;
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

            // 2. Provision workspace if task context is available
            WorkspaceInfo workspaceInfo = null;
            if (event.getTaskContext() != null) {
                try {
                    workspaceInfo = workspaceLifecycleService.provisionWorkspace(event.getTaskContext());
                    log.info("Provisioned workspace {} with branch {} for task {}",
                            workspaceInfo.getWorkspaceId(), workspaceInfo.getBranchName(), taskId);
                } catch (Exception e) {
                    log.warn("Failed to provision workspace for task {}: {}", taskId, e.getMessage());
                }
            }

            // 3. Start a conversation for the coding agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "CODING");

            // 4. Resolve configuration
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "CODING");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 5. Build the coding system prompt with tool definitions
            String systemPrompt = buildCodingPromptWithTools(
                    plan.getPlanContent(), toolRegistry.getAllToolDefinitions());

            // 6. Run the agentic loop
            String initialMessage = "Please implement the following plan. Use the provided tools "
                    + "to read files, write code, and run tests in the workspace.\n\nPlan:\n"
                    + plan.getPlanContent();

            UUID workspaceId = workspaceInfo != null ? workspaceInfo.getWorkspaceId() : null;
            AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                    conversation.getId(), tenantId, config,
                    systemPrompt, initialMessage, taskId, workspaceId,
                    MAX_ITERATIONS, NUDGE_MESSAGE, true,
                    conversationService, providerRegistry, toolExecutionEngine, objectMapper);

            // 7. Publish completion event
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, conversation.getId(),
                    "CODING", result.isSuccess(), result.getSummary(), natsEventPublisher);

            log.info("Code generation {} for task {} after {} iterations",
                    result.isSuccess() ? "completed" : "failed", taskId, result.getIterations());

        } catch (Exception e) {
            log.error("Code generation failed for task {}", taskId, e);
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, null,
                    "CODING", false, "Error: " + e.getMessage(), natsEventPublisher);
        }
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

        sb.append(AgentLoopSupport.renderToolDefinitions(tools));

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
}
