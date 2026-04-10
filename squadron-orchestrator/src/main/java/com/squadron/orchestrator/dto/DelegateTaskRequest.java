package com.squadron.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to delegate a task to an AI agent for processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegateTaskRequest {
    @NotBlank(message = "Agent type is required")
    private String agentType;

    /** Optional: specific agent name to use (e.g., "Sol", "Titan") */
    private String agentName;

    /** Optional: custom instructions for the agent */
    private String instructions;

    /** Target state to transition to before delegating (e.g., PLANNING, PROPOSE_CODE) */
    private String targetState;
}
