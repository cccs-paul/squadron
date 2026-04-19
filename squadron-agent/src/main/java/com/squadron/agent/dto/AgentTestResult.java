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
 * Result of an agent test execution, streamed back to the frontend via SSE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestResult {

    private UUID testId;
    private UUID agentConfigId;
    private String testMode;

    /** Overall status: RUNNING, SUCCESS, FAILURE, ERROR. */
    @Builder.Default
    private String status = "RUNNING";

    /** Summary of what the agent produced. */
    private String summary;

    /** The agent's output (generated plan / code / review). */
    private String agentOutput;

    /** Duration in milliseconds. */
    private Long durationMs;

    /** Verbose log entries for the expandable UI panel. */
    @Builder.Default
    private List<TestLogEntry> logEntries = new ArrayList<>();

    private Instant startedAt;
    private Instant completedAt;

    /**
     * A single log entry in the test execution timeline.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestLogEntry {
        private Instant timestamp;
        private String phase;
        private String message;
        /** One of: INFO, SUCCESS, WARNING, ERROR. */
        @Builder.Default
        private String level = "INFO";
    }
}
