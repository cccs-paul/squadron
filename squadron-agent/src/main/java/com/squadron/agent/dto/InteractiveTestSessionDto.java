package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing an interactive test session.
 * Interactive test sessions allow multi-turn chat with an agent from the
 * "My Agent Squadron" settings page. Unlike automated tests which generate
 * fake data and run non-interactively, interactive tests let the user
 * converse freely with the agent to evaluate its behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveTestSessionDto {

    private UUID sessionId;
    private UUID agentConfigId;
    private String agentName;
    private String provider;
    private String model;
    private String status; // ACTIVE, STREAMING, COMPLETED, ERROR
    private String containerId;
    private Instant createdAt;

    @Builder.Default
    private List<InteractiveTestMessage> messages = new ArrayList<>();

    /**
     * A single message in an interactive test conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractiveTestMessage {
        private UUID id;
        private String role; // USER, AGENT, SYSTEM
        private String content;
        private Integer tokenCount;
        private Instant createdAt;
    }
}
