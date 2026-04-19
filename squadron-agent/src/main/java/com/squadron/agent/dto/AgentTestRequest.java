package com.squadron.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to start an agent test execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestRequest {

    /** The ID of the agent configuration to test. */
    @NotNull(message = "Agent config ID is required")
    private UUID agentConfigId;

    /** The test mode: PLANNING, CODE_GENERATION, or CODE_REVIEW. */
    @NotBlank(message = "Test mode is required")
    private String testMode;
}
