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
 * Tool that lists files and directories inside a workspace container.
 * Supports recursive listing via {@code find} and non-recursive via {@code ls -la}.
 * Output is capped at 50KB.
 */
@Component
public class ListFilesTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ListFilesTool.class);

    static final String TOOL_NAME = "list_files";
    static final String TOOL_DESCRIPTION = "List files and directories in the workspace";
    static final String DEFAULT_PATH = "/workspace";
    static final int MAX_OUTPUT_BYTES = 50 * 1024; // 50KB

    private final WorkspaceClient workspaceClient;

    public ListFilesTool(WorkspaceClient workspaceClient) {
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
                                .description("Directory path to list")
                                .required(false)
                                .defaultValue(DEFAULT_PATH)
                                .build(),
                        ToolParameter.builder()
                                .name("recursive")
                                .type("boolean")
                                .description("Whether to list files recursively")
                                .required(false)
                                .defaultValue("false")
                                .build(),
                        ToolParameter.builder()
                                .name("pattern")
                                .type("string")
                                .description("File glob pattern to filter results (e.g. \"*.java\")")
                                .required(false)
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String path = context.getParameters().get("path") != null
                ? (String) context.getParameters().get("path")
                : DEFAULT_PATH;
        boolean recursive = resolveBoolean(context.getParameters().get("recursive"));
        String pattern = (String) context.getParameters().get("pattern");

        try {
            String cmd;
            if (recursive) {
                StringBuilder findCmd = new StringBuilder("find ")
                        .append(FileReadTool.shellEscape(path));
                if (pattern != null && !pattern.isBlank()) {
                    findCmd.append(" -name ").append(FileReadTool.shellEscape(pattern));
                }
                cmd = findCmd.toString();
            } else {
                cmd = "ls -la " + FileReadTool.shellEscape(path);
            }

            ExecResultDto result = workspaceClient.exec(
                    context.getWorkspaceId(), "bash", "-c", cmd);

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
                output = output.substring(0, MAX_OUTPUT_BYTES) + "\n\n[truncated — output exceeds 50KB]";
            }

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(true)
                    .output(output)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("ListFilesTool failed for path '{}' in workspace {}", path, context.getWorkspaceId(), e);
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Failed to list files: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Resolves a boolean from the parameter value, handling Boolean and String types.
     */
    static boolean resolveBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
