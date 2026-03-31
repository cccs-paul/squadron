package com.squadron.agent.service;

import com.squadron.agent.dto.ActiveAgentWorkDto;
import com.squadron.agent.dto.AgentActivityDto;
import com.squadron.agent.dto.AgentDashboardDto;
import com.squadron.agent.dto.AgentTypeSummaryDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides aggregated data for the agentic-work dashboard.
 * Queries conversation state and token usage to build a real-time view
 * of which agents are working, which are idle, and recent activity.
 */
@Service
@Transactional(readOnly = true)
public class AgentDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AgentDashboardService.class);

    /** The canonical set of agent types known to Squadron. */
    static final List<String> KNOWN_AGENT_TYPES = List.of(
            "PLANNING", "CODING", "REVIEW", "QA", "MERGE", "COVERAGE");

    /** Maximum number of recent activity items returned. */
    private static final int MAX_RECENT_ACTIVITY = 20;

    private final ConversationRepository conversationRepository;

    public AgentDashboardService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /**
     * Builds the full dashboard payload for the given tenant.
     */
    public AgentDashboardDto getDashboard(UUID tenantId) {
        log.debug("Building agent dashboard for tenant {}", tenantId);

        // All conversations for this tenant (for recent activity + summaries)
        List<Conversation> allConversations = conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);

        // Active conversations
        List<Conversation> activeConversations = allConversations.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .toList();

        // Total tokens across all conversations
        long totalTokensUsed = allConversations.stream()
                .mapToLong(Conversation::getTotalTokens)
                .sum();

        // Active work items
        List<ActiveAgentWorkDto> activeWork = activeConversations.stream()
                .map(this::toActiveWork)
                .toList();

        // Determine idle agent types (types with no ACTIVE conversation)
        var activeTypes = activeConversations.stream()
                .map(Conversation::getAgentType)
                .collect(Collectors.toSet());
        int idleAgents = (int) KNOWN_AGENT_TYPES.stream()
                .filter(t -> !activeTypes.contains(t))
                .count();

        // Recent activity (completed/closed conversations, sorted by updatedAt desc)
        List<AgentActivityDto> recentActivity = allConversations.stream()
                .limit(MAX_RECENT_ACTIVITY)
                .map(this::toActivity)
                .toList();

        // Breakdown by agent type
        Map<String, List<Conversation>> byType = allConversations.stream()
                .collect(Collectors.groupingBy(Conversation::getAgentType));

        List<AgentTypeSummaryDto> typeSummaries = KNOWN_AGENT_TYPES.stream()
                .map(type -> {
                    List<Conversation> convos = byType.getOrDefault(type, List.of());
                    int active = (int) convos.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
                    int completed = (int) convos.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count();
                    long tokens = convos.stream().mapToLong(Conversation::getTotalTokens).sum();
                    return AgentTypeSummaryDto.builder()
                            .agentType(type)
                            .activeCount(active)
                            .completedCount(completed)
                            .totalTokens(tokens)
                            .build();
                })
                .toList();

        return AgentDashboardDto.builder()
                .activeAgents(activeConversations.size())
                .idleAgents(idleAgents)
                .totalConversations(allConversations.size())
                .totalTokensUsed(totalTokensUsed)
                .activeWork(activeWork)
                .recentActivity(recentActivity)
                .agentTypeSummaries(typeSummaries)
                .build();
    }

    private ActiveAgentWorkDto toActiveWork(Conversation c) {
        return ActiveAgentWorkDto.builder()
                .conversationId(c.getId())
                .taskId(c.getTaskId())
                .agentType(c.getAgentType())
                .status(c.getStatus())
                .provider(c.getProvider())
                .model(c.getModel())
                .totalTokens(c.getTotalTokens())
                .startedAt(c.getCreatedAt())
                .lastActivityAt(c.getUpdatedAt())
                .build();
    }

    private AgentActivityDto toActivity(Conversation c) {
        String action = "ACTIVE".equals(c.getStatus()) ? "working" : "completed";
        return AgentActivityDto.builder()
                .conversationId(c.getId())
                .taskId(c.getTaskId())
                .agentType(c.getAgentType())
                .action(action)
                .totalTokens(c.getTotalTokens())
                .timestamp(c.getUpdatedAt())
                .build();
    }
}
