package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageByAgentDto {

    private String agentType;
    private long totalTokens;
    private double totalCost;
    private int invocations;
}
