package com.squadron.agent.tool;

/**
 * Interface for tools that coding/review/QA agents can invoke inside workspaces.
 * Each tool has a name, description, parameter schema, and execute method.
 *
 * <p>Implementations should be registered as Spring beans so they are
 * automatically discovered by the {@link ToolRegistry}.</p>
 */
public interface AgentTool {

    /** Unique tool name (e.g. "file_read", "shell_exec") */
    String getName();

    /** Human-readable description of what the tool does */
    String getDescription();

    /** Full definition including parameter schema */
    ToolDefinition getDefinition();

    /** Execute the tool with given parameters in the specified workspace */
    ToolResult execute(ToolExecutionContext context);
}
