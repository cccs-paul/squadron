package com.squadron.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine that looks up tools from the {@link ToolRegistry} and executes them,
 * measuring execution time and handling errors gracefully.
 */
@Service
public class ToolExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionEngine.class);

    private final ToolRegistry toolRegistry;

    public ToolExecutionEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Execute a single tool by name within the given context.
     *
     * @param toolName the name of the tool to execute
     * @param context  the execution context (workspace, task, parameters, etc.)
     * @return the tool result; on exception, returns a result with {@code success=false}
     */
    public ToolResult executeTool(String toolName, ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        try {
            AgentTool tool = toolRegistry.getTool(toolName);
            log.debug("Executing tool '{}' for task {}", toolName, context.getTaskId());

            ToolResult result = tool.execute(context);
            long elapsed = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(elapsed);

            log.debug("Tool '{}' completed in {}ms (success={})", toolName, elapsed, result.isSuccess());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Tool '{}' failed after {}ms: {}", toolName, elapsed, e.getMessage(), e);
            return ToolResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .error(e.getMessage())
                    .executionTimeMs(elapsed)
                    .build();
        }
    }

    /**
     * Execute multiple tool calls in sequence, using the base context but overriding
     * parameters for each individual call.
     *
     * @param toolCalls   the list of tool calls to execute
     * @param baseContext the base context (workspace, task, tenant, token)
     * @return list of results in the same order as the input calls
     */
    public List<ToolResult> executeTools(List<ToolCall> toolCalls, ToolExecutionContext baseContext) {
        List<ToolResult> results = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            ToolExecutionContext callContext = ToolExecutionContext.builder()
                    .workspaceId(baseContext.getWorkspaceId())
                    .taskId(baseContext.getTaskId())
                    .tenantId(baseContext.getTenantId())
                    .accessToken(baseContext.getAccessToken())
                    .parameters(call.getArguments())
                    .build();

            ToolResult result = executeTool(call.getToolName(), callContext);
            results.add(result);
        }
        return results;
    }
}
