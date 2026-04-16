package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight conversation summary for task detail view.
 * Contains metadata without full message history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDto {

    private UUID id;
    private UUID taskId;
    private String agentType;
    private String status;
    private String provider;
    private String model;
    private long totalTokens;
    private long messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
