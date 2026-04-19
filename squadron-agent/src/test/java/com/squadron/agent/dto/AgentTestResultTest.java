package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTestResultTest {

    @Test
    void should_buildWithAllFields() {
        UUID testId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        Instant now = Instant.now();

        AgentTestResult result = AgentTestResult.builder()
                .testId(testId)
                .agentConfigId(configId)
                .testMode("PLANNING")
                .status("SUCCESS")
                .summary("Test passed")
                .agentOutput("output content")
                .durationMs(1200L)
                .startedAt(now)
                .completedAt(now.plusMillis(1200))
                .build();

        assertEquals(testId, result.getTestId());
        assertEquals(configId, result.getAgentConfigId());
        assertEquals("PLANNING", result.getTestMode());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Test passed", result.getSummary());
        assertEquals("output content", result.getAgentOutput());
        assertEquals(1200L, result.getDurationMs());
    }

    @Test
    void should_defaultStatusToRunning() {
        AgentTestResult result = AgentTestResult.builder().build();
        assertEquals("RUNNING", result.getStatus());
    }

    @Test
    void should_defaultLogEntriesToEmptyList() {
        AgentTestResult result = AgentTestResult.builder().build();
        assertNotNull(result.getLogEntries());
        assertTrue(result.getLogEntries().isEmpty());
    }

    @Test
    void should_buildTestLogEntry() {
        Instant now = Instant.now();
        AgentTestResult.TestLogEntry entry = AgentTestResult.TestLogEntry.builder()
                .timestamp(now)
                .phase("INIT")
                .message("Starting test")
                .level("INFO")
                .build();

        assertEquals(now, entry.getTimestamp());
        assertEquals("INIT", entry.getPhase());
        assertEquals("Starting test", entry.getMessage());
        assertEquals("INFO", entry.getLevel());
    }

    @Test
    void should_defaultLogEntryLevelToInfo() {
        AgentTestResult.TestLogEntry entry = AgentTestResult.TestLogEntry.builder()
                .phase("TEST")
                .message("msg")
                .build();

        assertEquals("INFO", entry.getLevel());
    }
}
