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
 * Tool that executes a shell command inside a workspace container.
 * Commands run via {@code bash -c} with an optional working directory.
 * Output is capped at 50KB to avoid excessive token usage.
 */
@Component
public class ShellExecTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ShellExecTool.class);

    static final String TOOL_NAME = "shell_exec";
    static final String TOOL_DESCRIPTION = "Execute a shell command in the workspace";
    static final int MAX_OUTPUT_BYTES = 50 * 1024; // 50KB
    static final String DEFAULT_WORKDIR = "/workspace";

    private final WorkspaceClient workspaceClient;

    public ShellExecTool(WorkspaceClient workspaceClient) {
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
                .description(TOOL_DESCRIPTION + ". Timeout: commands have a maximum execution time enforced at the workspace level.")
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("command")
                                .type("string")
                                .description("The shell command to execute")
                                .required(true)
                                .build(),
                        ToolParameter.builder()
                                .name("workdir")
                                .type("string")
                                .description("Working directory for the command")
                                .required(false)
                                .defaultValue(DEFAULT_WORKDIR)
                                .build()
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String command = (String) context.getParameters().get("command");
        String workdir = context.getParameters().get("workdir") != null
                ? (String) context.getParameters().get("workdir")
                : DEFAULT_WORKDIR;

        if (command == null || command.isBlank()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Required parameter 'command' is missing or empty")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            String fullCommand = "cd " + FileReadTool.shellEscape(workdir) + " && " + command;
            ExecResultDto result = workspaceClient.exec(
                    context.getWorkspaceId(), "bash", "-c", fullCommand);

            String stdout = result.getStdout() != null ? result.getStdout() : "";
            String stderr = result.getStderr() != null ? result.getStderr() : "";

            StringBuilder output = new StringBuilder();
            if (!stdout.isEmpty()) {
                output.append(stdout);
            }
            if (!stderr.isEmpty()) {
                if (!output.isEmpty()) {
                    output.append("\n");
                }
                output.append("[stderr]\n").append(stderr);
            }

            String outputStr = output.toString();
            if (outputStr.length() > MAX_OUTPUT_BYTES) {
                outputStr = outputStr.substring(0, MAX_OUTPUT_BYTES) + "\n\n[truncated — output exceeds 50KB]";
            }

            if (result.getExitCode() != 0) {
                outputStr = "[exit code " + result.getExitCode() + "]\n" + outputStr;
            }

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(result.getExitCode() == 0)
                    .output(outputStr)
                    .error(result.getExitCode() != 0 ? "Command exited with code " + result.getExitCode() : null)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("ShellExecTool failed for command '{}' in workspace {}", command, context.getWorkspaceId(), e);
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .success(false)
                    .error("Failed to execute command: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
}
