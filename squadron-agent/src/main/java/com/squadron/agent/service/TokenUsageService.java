package com.squadron.agent.service;

import com.squadron.agent.dto.UsageByAgentDto;
import com.squadron.agent.dto.UsageSummaryDto;
import com.squadron.agent.entity.TokenUsage;
import com.squadron.agent.repository.TokenUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageService.class);

    private final TokenUsageRepository tokenUsageRepository;

    public TokenUsageService(TokenUsageRepository tokenUsageRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
    }

    public TokenUsage recordUsage(UUID tenantId, UUID teamId, UUID userId, UUID taskId,
                                   String agentType, String modelName,
                                   long inputTokens, long outputTokens) {
        long total = inputTokens + outputTokens;
        double cost = estimateCost(modelName, inputTokens, outputTokens);

        TokenUsage usage = TokenUsage.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .taskId(taskId)
                .agentType(agentType)
                .modelName(modelName)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(total)
                .estimatedCost(cost)
                .build();

        log.info("Recording token usage: tenant={}, agent={}, model={}, total={}",
                tenantId, agentType, modelName, total);

        return tokenUsageRepository.save(usage);
    }

    @Transactional(readOnly = true)
    public UsageSummaryDto getTenantSummary(UUID tenantId, Instant start, Instant end) {
        List<TokenUsage> usages = (start != null && end != null)
                ? tokenUsageRepository.findByTenantIdAndCreatedAtBetween(tenantId, start, end)
                : tokenUsageRepository.findByTenantId(tenantId);
        return aggregateUsages(usages);
    }

    @Transactional(readOnly = true)
    public UsageSummaryDto getUserSummary(UUID tenantId, UUID userId, Instant start, Instant end) {
        List<TokenUsage> usages = (start != null && end != null)
                ? tokenUsageRepository.findByTenantIdAndUserIdAndCreatedAtBetween(tenantId, userId, start, end)
                : tokenUsageRepository.findByTenantIdAndUserId(tenantId, userId);
        return aggregateUsages(usages);
    }

    @Transactional(readOnly = true)
    public UsageSummaryDto getTeamSummary(UUID tenantId, UUID teamId) {
        List<TokenUsage> usages = tokenUsageRepository.findByTenantIdAndTeamId(tenantId, teamId);
        return aggregateUsages(usages);
    }

    @Transactional(readOnly = true)
    public List<UsageByAgentDto> getUsageByAgentType(UUID tenantId) {
        List<TokenUsage> usages = tokenUsageRepository.findByTenantId(tenantId);
        return usages.stream()
                .collect(Collectors.groupingBy(TokenUsage::getAgentType))
                .entrySet().stream()
                .map(entry -> UsageByAgentDto.builder()
                        .agentType(entry.getKey())
                        .totalTokens(entry.getValue().stream().mapToLong(TokenUsage::getTotalTokens).sum())
                        .totalCost(entry.getValue().stream().mapToDouble(TokenUsage::getEstimatedCost).sum())
                        .invocations(entry.getValue().size())
                        .build())
                .toList();
    }

    private UsageSummaryDto aggregateUsages(List<TokenUsage> usages) {
        long totalInput = usages.stream().mapToLong(TokenUsage::getInputTokens).sum();
        long totalOutput = usages.stream().mapToLong(TokenUsage::getOutputTokens).sum();
        long totalTokens = usages.stream().mapToLong(TokenUsage::getTotalTokens).sum();
        double totalCost = usages.stream().mapToDouble(TokenUsage::getEstimatedCost).sum();

        return UsageSummaryDto.builder()
                .totalInputTokens(totalInput)
                .totalOutputTokens(totalOutput)
                .totalTokens(totalTokens)
                .totalCost(totalCost)
                .invocations(usages.size())
                .build();
    }

    double estimateCost(String modelName, long inputTokens, long outputTokens) {
        if (modelName == null) {
            return 0.0;
        }

        double inputCostPer1M;
        double outputCostPer1M;

        if (modelName.contains("gpt-4o")) {
            inputCostPer1M = 2.50;
            outputCostPer1M = 10.00;
        } else if (modelName.contains("gpt-4")) {
            inputCostPer1M = 30.00;
            outputCostPer1M = 60.00;
        } else if (modelName.contains("gpt-3.5")) {
            inputCostPer1M = 0.50;
            outputCostPer1M = 1.50;
        } else if (modelName.contains("claude")) {
            inputCostPer1M = 3.00;
            outputCostPer1M = 15.00;
        } else {
            // Ollama/local models: effectively free
            return 0.0;
        }

        return (inputTokens * inputCostPer1M / 1_000_000.0) + (outputTokens * outputCostPer1M / 1_000_000.0);
    }
}
