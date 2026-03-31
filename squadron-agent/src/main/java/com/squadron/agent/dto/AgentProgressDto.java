package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProgressDto {
    private UUID conversationId;
    private String agentType;
    private String phase;          // e.g. "PLANNING", "CODING", "REVIEWING", "TESTING"
    private String currentStep;    // e.g. "Analyzing codebase", "Writing tests"
    private int completedSteps;
    private int totalSteps;
    private List<ProgressItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressItem {
        private String content;
        private String status;     // "pending", "in_progress", "completed"
        private String priority;   // "high", "medium", "low"
    }
}
