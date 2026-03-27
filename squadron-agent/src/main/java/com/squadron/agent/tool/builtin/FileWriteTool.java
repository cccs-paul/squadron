package com.squadron.agent.tool.builtin;

import com.squadron.agent.tool.AgentTool;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Tool that writes content to a file inside a workspace container.
 * Creates parent directories if needed, then uses the workspace copy-to API.
 */
@Component
public class FileWriteTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileWriteTool.class);

    static final String TOOL_NAME = "file_write";
    static final String TOOL_DESCRIPTION = "Write content to a file in the workspace (creates parent dirs if needed)";

    private final WorkspaceClient workspaceClient;

    public FileWriteTool(WorkspaceClient workspaceClient) {
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
                                .build(),
                        ToolParameter.builder()
                                .name("content")
                                .type("string")
                                .description("The content to write to the file")
                                .required(true)
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String path = (String) context.getParameters().get("path");
        String content = (String) context.getParameters().get("content");

        if (path == null || path.isBlank()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Required parameter 'path' is missing or empty")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        if (content == null) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Required parameter 'content' is missing")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            // Create parent directories
            String parentDir = Path.of(path).getParent() != null
                    ? Path.of(path).getParent().toString()
                    : "/workspace";

            ExecResultDto mkdirResult = workspaceClient.exec(
                    context.getWorkspaceId(), "bash", "-c", "mkdir -p " + FileReadTool.shellEscape(parentDir));

            if (mkdirResult.getExitCode() != 0) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .success(false)
                        .error("Failed to create parent directory: " + mkdirResult.getStderr())
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Write the file
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            workspaceClient.writeFile(context.getWorkspaceId(), path, contentBytes);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(true)
                    .output("Successfully wrote " + contentBytes.length + " bytes to " + path)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("FileWriteTool failed for path '{}' in workspace {}", path, context.getWorkspaceId(), e);
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Failed to write file: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
}
