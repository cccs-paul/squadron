package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a recent agent activity event for the dashboard timeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActivityDto {

    private UUID conversationId;
    private UUID taskId;
    private String agentType;
    private String action;
    private long totalTokens;
    private Instant timestamp;
}
