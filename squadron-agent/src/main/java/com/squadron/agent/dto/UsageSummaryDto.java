package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryDto {

    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalTokens;
    private double totalCost;
    private int invocations;
}
