package com.squadron.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned after executing an {@link AgentTool}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** Name of the tool that was executed */
    private String toolName;

    /** Whether the execution succeeded */
    private boolean success;

    /** The tool's output/result content */
    private String output;

    /** Error message if not successful */
    private String error;

    /** Execution time in milliseconds */
    private long executionTimeMs;
}
