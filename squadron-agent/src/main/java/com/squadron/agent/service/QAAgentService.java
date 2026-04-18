package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.QAReportDto;
import com.squadron.agent.dto.WorkspaceInfo;
import com.squadron.agent.entity.Conversation;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private static final String NUDGE_MESSAGE =
            "Please continue the QA analysis. Use the available tools "
                    + "to run tests, check coverage, and identify gaps. When you're done, "
                    + "include [DONE] in your response along with your QA verdict "
                    + "([QA_PASS], [QA_CONDITIONAL_PASS], or [QA_FAIL]).";

    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionEngine toolExecutionEngine;
    private final NatsEventPublisher natsEventPublisher;
    private final CoverageService coverageService;
    private final ObjectMapper objectMapper;
    private final WorkspaceLifecycleService workspaceLifecycleService;

    public QAAgentService(ConversationService conversationService,
                          SquadronConfigService configService,
                          AgentProviderRegistry providerRegistry,
                          SystemPromptBuilder promptBuilder,
                          ToolRegistry toolRegistry,
                          ToolExecutionEngine toolExecutionEngine,
                          NatsEventPublisher natsEventPublisher,
                          CoverageService coverageService,
                          ObjectMapper objectMapper,
                          WorkspaceLifecycleService workspaceLifecycleService) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutionEngine = toolExecutionEngine;
        this.natsEventPublisher = natsEventPublisher;
        this.coverageService = coverageService;
        this.objectMapper = objectMapper;
        this.workspaceLifecycleService = workspaceLifecycleService;
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
            // 1. Find or provision workspace for QA
            WorkspaceInfo workspaceInfo = null;
            if (event.getTaskContext() != null) {
                try {
                    workspaceInfo = workspaceLifecycleService.findOrProvisionWorkspace(event.getTaskContext());
                    log.info("Using workspace {} for QA of task {}",
                            workspaceInfo.getWorkspaceId(), taskId);
                } catch (Exception e) {
                    log.warn("Failed to find/provision workspace for QA of task {}: {}", taskId, e.getMessage());
                }
            }

            // 2. Start a conversation for the QA agent
            Conversation conversation = conversationService.startConversation(
                    tenantId, taskId, userId, "QA");

            // 3. Resolve configuration for the QA agent type
            AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, "QA");
            if (config == null) {
                config = AgentConfigDto.builder().build();
            }

            // 4. Build the QA system prompt with tool definitions
            String taskDescription = event.getReason() != null
                    ? event.getReason()
                    : "Perform QA analysis on the code changes for task " + taskId;
            String systemPrompt = buildQAPromptWithTools(
                    taskDescription, null, toolRegistry.getAllToolDefinitions());

            // 5. Run the agentic loop
            String initialMessage = "Please perform a thorough QA analysis of the code changes "
                    + "for task " + taskId + ". Use the provided tools to run tests, check coverage, "
                    + "read source files, and identify test gaps. If you find missing tests, "
                    + "generate them.\n\nTask: " + taskDescription;

            AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                    conversation.getId(), tenantId, config,
                    systemPrompt, initialMessage, taskId, null,
                    MAX_ITERATIONS, NUDGE_MESSAGE, false,
                    conversationService, providerRegistry, toolExecutionEngine, objectMapper);

            // 6. Parse QA verdict from the response
            String verdict = parseQAVerdict(result.getSummary());

            // 7. Build QA report
            QAReportDto report = QAReportDto.builder()
                    .taskId(taskId)
                    .tenantId(tenantId)
                    .verdict(verdict)
                    .summary(result.getSummary())
                    .createdAt(Instant.now())
                    .build();

            boolean success = !"FAIL".equals(verdict);

            // 8. Publish completion event
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, conversation.getId(),
                    "QA", success, result.getSummary(), natsEventPublisher);

            log.info("QA analysis {} for task {} with verdict {} after {} iterations",
                    success ? "completed" : "failed", taskId, verdict, result.getIterations());

        } catch (Exception e) {
            log.error("QA analysis failed for task {}", taskId, e);
            AgentLoopSupport.publishCompletedEvent(tenantId, taskId, null,
                    "QA", false, "Error: " + e.getMessage(), natsEventPublisher);
        }
    }

    /**
     * Builds a QA system prompt that includes tool definitions so the LLM
     * knows which tools are available and how to invoke them.
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

        sb.append(AgentLoopSupport.renderToolDefinitions(tools));

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
}
