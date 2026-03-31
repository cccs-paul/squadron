package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an actively running agent conversation with task context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveAgentWorkDto {

    private UUID conversationId;
    private UUID taskId;
    private String agentType;
    private String status;
    private String provider;
    private String model;
    private long totalTokens;
    private Instant startedAt;
    private Instant lastActivityAt;
}
