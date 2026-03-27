package com.squadron.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a single tool invocation request from an LLM response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** Unique ID for this tool call (from the LLM response) */
    private String id;

    /** Name of the tool to invoke */
    private String toolName;

    /** The argument values for the tool */
    private Map<String, Object> arguments;
}
