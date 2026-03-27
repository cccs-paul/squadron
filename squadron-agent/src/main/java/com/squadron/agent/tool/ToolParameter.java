package com.squadron.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a single parameter accepted by an {@link AgentTool}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolParameter {

    /** Parameter name (e.g. "filePath", "command") */
    private String name;

    /** JSON-Schema type: "string", "integer", "boolean", "array" */
    private String type;

    /** Human-readable description of the parameter */
    private String description;

    /** Whether the parameter is required */
    private boolean required;

    /** Optional default value (nullable) */
    private String defaultValue;
}
