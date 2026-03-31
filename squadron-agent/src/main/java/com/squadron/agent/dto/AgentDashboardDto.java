package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated dashboard data for the agentic work overview.
 * Shows active/idle agent counts, active work items, recent activity, and per-type summaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDashboardDto {

    /** Number of agents (conversations) currently active. */
    private int activeAgents;

    /** Number of agent types with no active conversation. */
    private int idleAgents;

    /** Total conversations across all states. */
    private long totalConversations;

    /** Total tokens consumed across all conversations. */
    private long totalTokensUsed;

    /** Details of each actively working agent. */
    private List<ActiveAgentWorkDto> activeWork;

    /** Recent completed or notable agent activities. */
    private List<AgentActivityDto> recentActivity;

    /** Breakdown by agent type. */
    private List<AgentTypeSummaryDto> agentTypeSummaries;
}
