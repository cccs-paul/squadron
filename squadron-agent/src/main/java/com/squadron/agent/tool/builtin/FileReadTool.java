package com.squadron.agent.tool.builtin;

import com.squadron.agent.tool.AgentTool;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool that reads the contents of a file inside a workspace container.
 * Uses {@code cat} via the workspace exec API.
 */
@Component
public class FileReadTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileReadTool.class);

    static final String TOOL_NAME = "file_read";
    static final String TOOL_DESCRIPTION = "Read the contents of a file in the workspace";
    static final int MAX_OUTPUT_BYTES = 100 * 1024; // 100KB

    private final WorkspaceClient workspaceClient;

    public FileReadTool(WorkspaceClient workspaceClient) {
        this.workspaceClient = workspaceClient;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("path")
                                .type("string")
                                .description("Absolute path to the file inside the container (should start with /workspace/)")
                                .required(true)
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String path = (String) context.getParameters().get("path");

        if (path == null || path.isBlank()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Required parameter 'path' is missing or empty")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            ExecResultDto result = workspaceClient.exec(
                    context.getWorkspaceId(), "bash", "-c", "cat " + shellEscape(path));

            if (result.getExitCode() != 0) {
                String error = result.getStderr() != null && !result.getStderr().isBlank()
                        ? result.getStderr()
                        : "Command exited with code " + result.getExitCode();
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .success(false)
                        .error(error)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            String output = result.getStdout() != null ? result.getStdout() : "";
            if (output.length() > MAX_OUTPUT_BYTES) {
                output = output.substring(0, MAX_OUTPUT_BYTES) + "\n\n[truncated — file exceeds 100KB]";
            }

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(true)
                    .output(output)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("FileReadTool failed for path '{}' in workspace {}", path, context.getWorkspaceId(), e);
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Failed to read file: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Basic shell escaping: wraps value in single quotes, escaping embedded single quotes.
     */
    static String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
