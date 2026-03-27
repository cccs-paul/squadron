package com.squadron.agent.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageTest {

    @Test
    void should_buildTokenUsage_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TokenUsage usage = TokenUsage.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .taskId(taskId)
                .agentType("CODING")
                .modelName("gpt-4o")
                .inputTokens(1000)
                .outputTokens(500)
                .totalTokens(1500)
                .estimatedCost(0.0075)
                .build();

        assertEquals(id, usage.getId());
        assertEquals(tenantId, usage.getTenantId());
        assertEquals(teamId, usage.getTeamId());
        assertEquals(userId, usage.getUserId());
        assertEquals(taskId, usage.getTaskId());
        assertEquals("CODING", usage.getAgentType());
        assertEquals("gpt-4o", usage.getModelName());
        assertEquals(1000, usage.getInputTokens());
        assertEquals(500, usage.getOutputTokens());
        assertEquals(1500, usage.getTotalTokens());
        assertEquals(0.0075, usage.getEstimatedCost(), 0.0001);
    }

    @Test
    void should_haveDefaultValues_when_usingBuilder() {
        TokenUsage usage = TokenUsage.builder()
                .tenantId(UUID.randomUUID())
                .agentType("PLANNING")
                .build();

        assertEquals(0, usage.getInputTokens());
        assertEquals(0, usage.getOutputTokens());
        assertEquals(0, usage.getTotalTokens());
        assertEquals(0.0, usage.getEstimatedCost());
        assertNull(usage.getTeamId());
        assertNull(usage.getUserId());
        assertNull(usage.getTaskId());
        assertNull(usage.getModelName());
    }

    @Test
    void should_setTimestamp_when_onCreateCalled() {
        TokenUsage usage = TokenUsage.builder()
                .tenantId(UUID.randomUUID())
                .agentType("CODING")
                .build();

        usage.onCreate();

        assertNotNull(usage.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        TokenUsage usage = new TokenUsage();
        UUID tenantId = UUID.randomUUID();

        usage.setTenantId(tenantId);
        usage.setAgentType("REVIEW");
        usage.setModelName("claude-3-sonnet");
        usage.setInputTokens(2000);
        usage.setOutputTokens(1000);
        usage.setTotalTokens(3000);
        usage.setEstimatedCost(0.021);

        assertEquals(tenantId, usage.getTenantId());
        assertEquals("REVIEW", usage.getAgentType());
        assertEquals("claude-3-sonnet", usage.getModelName());
        assertEquals(2000, usage.getInputTokens());
        assertEquals(1000, usage.getOutputTokens());
        assertEquals(3000, usage.getTotalTokens());
        assertEquals(0.021, usage.getEstimatedCost(), 0.0001);
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TokenUsage u1 = TokenUsage.builder().id(id).tenantId(tenantId).agentType("CODING")
                .inputTokens(0).outputTokens(0).totalTokens(0).estimatedCost(0.0).build();
        TokenUsage u2 = TokenUsage.builder().id(id).tenantId(tenantId).agentType("CODING")
                .inputTokens(0).outputTokens(0).totalTokens(0).estimatedCost(0.0).build();

        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TokenUsage u1 = TokenUsage.builder().tenantId(UUID.randomUUID()).agentType("CODING").build();
        TokenUsage u2 = TokenUsage.builder().tenantId(UUID.randomUUID()).agentType("REVIEW").build();

        assertNotEquals(u1, u2);
    }

    @Test
    void should_createTokenUsage_when_usingNoArgsConstructor() {
        TokenUsage usage = new TokenUsage();
        assertNull(usage.getId());
        assertNull(usage.getTenantId());
        assertNull(usage.getAgentType());
        assertNull(usage.getCreatedAt());
    }

    @Test
    void should_createTokenUsage_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        TokenUsage usage = new TokenUsage(id, tenantId, teamId, userId, taskId,
                "QA", "gpt-3.5-turbo", 500, 200, 700, 0.00055, now);

        assertEquals(id, usage.getId());
        assertEquals(tenantId, usage.getTenantId());
        assertEquals(teamId, usage.getTeamId());
        assertEquals(userId, usage.getUserId());
        assertEquals(taskId, usage.getTaskId());
        assertEquals("QA", usage.getAgentType());
        assertEquals("gpt-3.5-turbo", usage.getModelName());
        assertEquals(500, usage.getInputTokens());
        assertEquals(200, usage.getOutputTokens());
        assertEquals(700, usage.getTotalTokens());
        assertEquals(0.00055, usage.getEstimatedCost(), 0.00001);
        assertEquals(now, usage.getCreatedAt());
    }
}
