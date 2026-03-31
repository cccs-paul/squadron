package com.squadron.agent.service;

import com.squadron.agent.dto.AgentDashboardDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDashboardServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    private AgentDashboardService dashboardService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        dashboardService = new AgentDashboardService(conversationRepository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void should_returnEmptyDashboard_when_noConversations() {
        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(Collections.emptyList());

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertNotNull(result);
        assertEquals(0, result.getActiveAgents());
        assertEquals(AgentDashboardService.KNOWN_AGENT_TYPES.size(), result.getIdleAgents());
        assertEquals(0, result.getTotalConversations());
        assertEquals(0, result.getTotalTokensUsed());
        assertTrue(result.getActiveWork().isEmpty());
        assertTrue(result.getRecentActivity().isEmpty());
        assertEquals(AgentDashboardService.KNOWN_AGENT_TYPES.size(), result.getAgentTypeSummaries().size());

        verify(conversationRepository).findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Test
    void should_countActiveAgents_when_activeConversationsExist() {
        List<Conversation> conversations = List.of(
                buildConversation("CODING", "ACTIVE", 500L),
                buildConversation("REVIEW", "ACTIVE", 300L),
                buildConversation("PLANNING", "COMPLETED", 200L)
        );

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(2, result.getActiveAgents());
        assertEquals(4, result.getIdleAgents()); // 6 known types - 2 active = 4 idle
        assertEquals(3, result.getTotalConversations());
        assertEquals(1000L, result.getTotalTokensUsed());
    }

    @Test
    void should_populateActiveWork_when_activeConversationsExist() {
        Conversation active = buildConversation("CODING", "ACTIVE", 750L);

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(List.of(active));

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(1, result.getActiveWork().size());
        assertEquals("CODING", result.getActiveWork().get(0).getAgentType());
        assertEquals("ACTIVE", result.getActiveWork().get(0).getStatus());
        assertEquals(750L, result.getActiveWork().get(0).getTotalTokens());
    }

    @Test
    void should_populateRecentActivity_when_conversationsExist() {
        List<Conversation> conversations = List.of(
                buildConversation("CODING", "COMPLETED", 500L),
                buildConversation("REVIEW", "ACTIVE", 300L)
        );

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(2, result.getRecentActivity().size());
        assertEquals("completed", result.getRecentActivity().get(0).getAction());
        assertEquals("working", result.getRecentActivity().get(1).getAction());
    }

    @Test
    void should_buildAgentTypeSummaries_when_conversationsExist() {
        List<Conversation> conversations = List.of(
                buildConversation("CODING", "ACTIVE", 100L),
                buildConversation("CODING", "COMPLETED", 200L),
                buildConversation("CODING", "COMPLETED", 300L),
                buildConversation("REVIEW", "ACTIVE", 50L)
        );

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        var codingSummary = result.getAgentTypeSummaries().stream()
                .filter(s -> "CODING".equals(s.getAgentType()))
                .findFirst().orElseThrow();

        assertEquals(1, codingSummary.getActiveCount());
        assertEquals(2, codingSummary.getCompletedCount());
        assertEquals(600L, codingSummary.getTotalTokens());

        var reviewSummary = result.getAgentTypeSummaries().stream()
                .filter(s -> "REVIEW".equals(s.getAgentType()))
                .findFirst().orElseThrow();

        assertEquals(1, reviewSummary.getActiveCount());
        assertEquals(0, reviewSummary.getCompletedCount());
        assertEquals(50L, reviewSummary.getTotalTokens());
    }

    @Test
    void should_computeIdleAgentsCorrectly_when_allTypesActive() {
        List<Conversation> conversations = AgentDashboardService.KNOWN_AGENT_TYPES.stream()
                .map(type -> buildConversation(type, "ACTIVE", 100L))
                .toList();

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(AgentDashboardService.KNOWN_AGENT_TYPES.size(), result.getActiveAgents());
        assertEquals(0, result.getIdleAgents());
    }

    @Test
    void should_limitRecentActivity_toMaxItems() {
        // Create more conversations than MAX_RECENT_ACTIVITY (20)
        List<Conversation> conversations = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            conversations.add(buildConversation("CODING", "COMPLETED", 10L));
        }

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(20, result.getRecentActivity().size());
    }

    @Test
    void should_handleUnknownAgentType_inConversation() {
        List<Conversation> conversations = List.of(
                buildConversation("CUSTOM_AGENT", "ACTIVE", 100L)
        );

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        // Active count still reflects the CUSTOM_AGENT conversation
        assertEquals(1, result.getActiveAgents());
        // All 6 known types are idle (CUSTOM_AGENT is not in KNOWN_AGENT_TYPES)
        assertEquals(6, result.getIdleAgents());
    }

    @Test
    void should_sumTokensCorrectly_acrossAllConversations() {
        List<Conversation> conversations = List.of(
                buildConversation("PLANNING", "COMPLETED", 1000L),
                buildConversation("CODING", "ACTIVE", 2500L),
                buildConversation("REVIEW", "COMPLETED", 500L)
        );

        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(conversations);

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(4000L, result.getTotalTokensUsed());
    }

    @Test
    void should_returnAllKnownTypes_inSummaries_evenWhenNoConversations() {
        when(conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .thenReturn(Collections.emptyList());

        AgentDashboardDto result = dashboardService.getDashboard(tenantId);

        assertEquals(AgentDashboardService.KNOWN_AGENT_TYPES.size(), result.getAgentTypeSummaries().size());
        for (var summary : result.getAgentTypeSummaries()) {
            assertTrue(AgentDashboardService.KNOWN_AGENT_TYPES.contains(summary.getAgentType()));
            assertEquals(0, summary.getActiveCount());
            assertEquals(0, summary.getCompletedCount());
            assertEquals(0L, summary.getTotalTokens());
        }
    }

    private Conversation buildConversation(String agentType, String status, long totalTokens) {
        Instant now = Instant.now();
        return Conversation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .agentType(agentType)
                .status(status)
                .totalTokens(totalTokens)
                .provider("openai")
                .model("gpt-4o")
                .createdAt(now.minusSeconds(600))
                .updatedAt(now)
                .build();
    }
}
