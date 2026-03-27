package com.squadron.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Describes an {@link AgentTool}: its name, description, and accepted parameters.
 * This DTO is sent to the LLM so it knows which tools are available.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** Unique tool name (e.g. "file_read", "shell_exec") */
    private String name;

    /** Human-readable description of what the tool does */
    private String description;

    /** The parameters this tool accepts */
    private List<ToolParameter> parameters;
}
