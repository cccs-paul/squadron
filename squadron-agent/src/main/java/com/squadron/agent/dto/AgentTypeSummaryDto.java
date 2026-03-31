package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of agent usage broken down by agent type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTypeSummaryDto {

    private String agentType;
    private int activeCount;
    private int completedCount;
    private long totalTokens;
}
