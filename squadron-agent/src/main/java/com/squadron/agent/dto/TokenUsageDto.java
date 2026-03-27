package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageDto {

    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private String agentType;
    private Long totalTokens;
    private String period;
}
