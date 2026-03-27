package com.squadron.agent.service;

import com.squadron.agent.dto.UsageByAgentDto;
import com.squadron.agent.dto.UsageSummaryDto;
import com.squadron.agent.entity.TokenUsage;
import com.squadron.agent.repository.TokenUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageServiceTest {

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    private TokenUsageService tokenUsageService;

    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        tokenUsageService = new TokenUsageService(tokenUsageRepository);
        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        taskId = UUID.randomUUID();
    }

    @Test
    void should_recordUsage_successfully() {
        TokenUsage saved = TokenUsage.builder()
                .id(UUID.randomUUID())
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

        when(tokenUsageRepository.save(any(TokenUsage.class))).thenReturn(saved);

        TokenUsage result = tokenUsageService.recordUsage(
                tenantId, teamId, userId, taskId, "CODING", "gpt-4o", 1000, 500);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals("CODING", result.getAgentType());
        assertEquals(1500, result.getTotalTokens());

        ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());
        TokenUsage captured = captor.getValue();
        assertEquals(1000, captured.getInputTokens());
        assertEquals(500, captured.getOutputTokens());
        assertEquals(1500, captured.getTotalTokens());
        assertTrue(captured.getEstimatedCost() > 0);
    }

    @Test
    void should_getTenantSummary_withDateRange() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-31T23:59:59Z");

        List<TokenUsage> usages = List.of(
                buildUsage("CODING", "gpt-4o", 1000, 500, 1500, 0.0075),
                buildUsage("PLANNING", "gpt-4o", 2000, 1000, 3000, 0.015)
        );

        when(tokenUsageRepository.findByTenantIdAndCreatedAtBetween(tenantId, start, end))
                .thenReturn(usages);

        UsageSummaryDto result = tokenUsageService.getTenantSummary(tenantId, start, end);

        assertEquals(3000, result.getTotalInputTokens());
        assertEquals(1500, result.getTotalOutputTokens());
        assertEquals(4500, result.getTotalTokens());
        assertEquals(0.0225, result.getTotalCost(), 0.0001);
        assertEquals(2, result.getInvocations());
    }

    @Test
    void should_getTenantSummary_withoutDateRange() {
        List<TokenUsage> usages = List.of(
                buildUsage("CODING", "gpt-4o", 500, 200, 700, 0.0035)
        );

        when(tokenUsageRepository.findByTenantId(tenantId)).thenReturn(usages);

        UsageSummaryDto result = tokenUsageService.getTenantSummary(tenantId, null, null);

        assertEquals(500, result.getTotalInputTokens());
        assertEquals(200, result.getTotalOutputTokens());
        assertEquals(700, result.getTotalTokens());
        assertEquals(1, result.getInvocations());
    }

    @Test
    void should_getUserSummary_withDateRange() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-31T23:59:59Z");

        List<TokenUsage> usages = List.of(
                buildUsage("REVIEW", "claude-3-sonnet", 3000, 1500, 4500, 0.03)
        );

        when(tokenUsageRepository.findByTenantIdAndUserIdAndCreatedAtBetween(tenantId, userId, start, end))
                .thenReturn(usages);

        UsageSummaryDto result = tokenUsageService.getUserSummary(tenantId, userId, start, end);

        assertEquals(3000, result.getTotalInputTokens());
        assertEquals(1500, result.getTotalOutputTokens());
        assertEquals(4500, result.getTotalTokens());
        assertEquals(1, result.getInvocations());
    }

    @Test
    void should_getUserSummary_withoutDateRange() {
        List<TokenUsage> usages = List.of(
                buildUsage("CODING", "gpt-4", 1000, 500, 1500, 0.06)
        );

        when(tokenUsageRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(usages);

        UsageSummaryDto result = tokenUsageService.getUserSummary(tenantId, userId, null, null);

        assertEquals(1000, result.getTotalInputTokens());
        assertEquals(500, result.getTotalOutputTokens());
        assertEquals(1500, result.getTotalTokens());
        assertEquals(1, result.getInvocations());
    }

    @Test
    void should_getTeamSummary() {
        List<TokenUsage> usages = List.of(
                buildUsage("CODING", "gpt-4o", 1000, 500, 1500, 0.0075),
                buildUsage("REVIEW", "gpt-4o", 2000, 800, 2800, 0.013)
        );

        when(tokenUsageRepository.findByTenantIdAndTeamId(tenantId, teamId)).thenReturn(usages);

        UsageSummaryDto result = tokenUsageService.getTeamSummary(tenantId, teamId);

        assertEquals(3000, result.getTotalInputTokens());
        assertEquals(1300, result.getTotalOutputTokens());
        assertEquals(4300, result.getTotalTokens());
        assertEquals(2, result.getInvocations());
    }

    @Test
    void should_getUsageByAgentType() {
        List<TokenUsage> usages = List.of(
                buildUsage("CODING", "gpt-4o", 1000, 500, 1500, 0.0075),
                buildUsage("CODING", "gpt-4o", 2000, 1000, 3000, 0.015),
                buildUsage("REVIEW", "claude-3-sonnet", 3000, 1500, 4500, 0.03)
        );

        when(tokenUsageRepository.findByTenantId(tenantId)).thenReturn(usages);

        List<UsageByAgentDto> result = tokenUsageService.getUsageByAgentType(tenantId);

        assertEquals(2, result.size());

        UsageByAgentDto codingDto = result.stream()
                .filter(d -> "CODING".equals(d.getAgentType()))
                .findFirst().orElseThrow();
        assertEquals(4500, codingDto.getTotalTokens());
        assertEquals(0.0225, codingDto.getTotalCost(), 0.0001);
        assertEquals(2, codingDto.getInvocations());

        UsageByAgentDto reviewDto = result.stream()
                .filter(d -> "REVIEW".equals(d.getAgentType()))
                .findFirst().orElseThrow();
        assertEquals(4500, reviewDto.getTotalTokens());
        assertEquals(0.03, reviewDto.getTotalCost(), 0.0001);
        assertEquals(1, reviewDto.getInvocations());
    }

    @Test
    void should_estimateCost_forGpt4o() {
        double cost = tokenUsageService.estimateCost("gpt-4o", 1_000_000, 1_000_000);
        // input: 2.50, output: 10.00 => 12.50
        assertEquals(12.50, cost, 0.01);
    }

    @Test
    void should_estimateCost_forGpt4() {
        double cost = tokenUsageService.estimateCost("gpt-4-turbo", 1_000_000, 1_000_000);
        // input: 30.00, output: 60.00 => 90.00
        assertEquals(90.00, cost, 0.01);
    }

    @Test
    void should_estimateCost_forGpt35() {
        double cost = tokenUsageService.estimateCost("gpt-3.5-turbo", 1_000_000, 1_000_000);
        // input: 0.50, output: 1.50 => 2.00
        assertEquals(2.00, cost, 0.01);
    }

    @Test
    void should_estimateCost_forClaude() {
        double cost = tokenUsageService.estimateCost("claude-3-sonnet", 1_000_000, 1_000_000);
        // input: 3.00, output: 15.00 => 18.00
        assertEquals(18.00, cost, 0.01);
    }

    @Test
    void should_estimateCost_forOllamaModels() {
        double cost = tokenUsageService.estimateCost("llama3", 1_000_000, 1_000_000);
        assertEquals(0.0, cost);
    }

    @Test
    void should_estimateCost_forNullModel() {
        double cost = tokenUsageService.estimateCost(null, 1_000_000, 1_000_000);
        assertEquals(0.0, cost);
    }

    @Test
    void should_handleEmptyUsages_forTenantSummary() {
        when(tokenUsageRepository.findByTenantId(tenantId)).thenReturn(List.of());

        UsageSummaryDto result = tokenUsageService.getTenantSummary(tenantId, null, null);

        assertEquals(0, result.getTotalInputTokens());
        assertEquals(0, result.getTotalOutputTokens());
        assertEquals(0, result.getTotalTokens());
        assertEquals(0.0, result.getTotalCost());
        assertEquals(0, result.getInvocations());
    }

    @Test
    void should_handleEmptyUsages_forUsageByAgentType() {
        when(tokenUsageRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<UsageByAgentDto> result = tokenUsageService.getUsageByAgentType(tenantId);

        assertTrue(result.isEmpty());
    }

    private TokenUsage buildUsage(String agentType, String modelName,
                                   long inputTokens, long outputTokens,
                                   long totalTokens, double estimatedCost) {
        return TokenUsage.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .taskId(taskId)
                .agentType(agentType)
                .modelName(modelName)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .estimatedCost(estimatedCost)
                .build();
    }
}
