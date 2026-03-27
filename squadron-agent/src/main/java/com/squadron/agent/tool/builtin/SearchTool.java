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
 * Tool that searches for text patterns in files within a workspace container.
 * Uses {@code grep -rn} with optional file glob and result limit.
 */
@Component
public class SearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SearchTool.class);

    static final String TOOL_NAME = "search";
    static final String TOOL_DESCRIPTION = "Search for text patterns in files within the workspace (uses grep)";
    static final String DEFAULT_PATH = "/workspace";
    static final int DEFAULT_MAX_RESULTS = 50;

    private final WorkspaceClient workspaceClient;

    public SearchTool(WorkspaceClient workspaceClient) {
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
                                .name("pattern")
                                .type("string")
                                .description("The text pattern to search for (grep regex)")
                                .required(true)
                                .build(),
                        ToolParameter.builder()
                                .name("path")
                                .type("string")
                                .description("Directory to search in")
                                .required(false)
                                .defaultValue(DEFAULT_PATH)
                                .build(),
                        ToolParameter.builder()
                                .name("include")
                                .type("string")
                                .description("File glob pattern to filter files (e.g. \"*.java\")")
                                .required(false)
                                .build(),
                        ToolParameter.builder()
                                .name("maxResults")
                                .type("integer")
                                .description("Maximum number of matching lines to return")
                                .required(false)
                                .defaultValue(String.valueOf(DEFAULT_MAX_RESULTS))
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String pattern = (String) context.getParameters().get("pattern");
        String path = context.getParameters().get("path") != null
                ? (String) context.getParameters().get("path")
                : DEFAULT_PATH;
        String include = (String) context.getParameters().get("include");
        int maxResults = resolveMaxResults(context.getParameters().get("maxResults"));

        if (pattern == null || pattern.isBlank()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Required parameter 'pattern' is missing or empty")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            StringBuilder cmd = new StringBuilder("grep -rn");
            if (include != null && !include.isBlank()) {
                cmd.append(" --include=").append(FileReadTool.shellEscape(include));
            }
            cmd.append(" ").append(FileReadTool.shellEscape(pattern));
            cmd.append(" ").append(FileReadTool.shellEscape(path));
            cmd.append(" | head -").append(maxResults);

            ExecResultDto result = workspaceClient.exec(
                    context.getWorkspaceId(), "bash", "-c", cmd.toString());

            // grep returns exit code 1 when no matches found — that's not an error
            if (result.getExitCode() != 0 && result.getExitCode() != 1) {
                String error = result.getStderr() != null && !result.getStderr().isBlank()
                        ? result.getStderr()
                        : "grep exited with code " + result.getExitCode();
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .success(false)
                        .error(error)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            String output = result.getStdout() != null ? result.getStdout() : "";
            if (output.isBlank()) {
                output = "No matches found for pattern: " + pattern;
            }

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(true)
                    .output(output)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("SearchTool failed for pattern '{}' in workspace {}", pattern, context.getWorkspaceId(), e);
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Failed to search: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Resolves maxResults from the parameter value, handling both Integer and String types.
     */
    static int resolveMaxResults(Object value) {
        if (value == null) {
            return DEFAULT_MAX_RESULTS;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_RESULTS;
        }
    }
}
