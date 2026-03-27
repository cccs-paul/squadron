package com.squadron.agent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Captures the outcome of an agentic tool-calling loop executed by
 * {@link CodingAgentService}. Includes whether the loop succeeded,
 * how many LLM iterations were performed, and a human-readable summary.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentLoopResult {

    /** Whether the agent completed its task successfully */
    private boolean success;

    /** Number of LLM call iterations performed */
    private int iterations;

    /** Human-readable summary of the outcome */
    private String summary;
}
