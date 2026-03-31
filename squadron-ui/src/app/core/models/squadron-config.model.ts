/**
 * User agent squadron configuration model.
 * Mirrors com.squadron.agent.entity.UserAgentConfig / UserAgentConfigDto.
 */
export interface UserAgentConfig {
  id?: string;
  tenantId?: string;
  userId?: string;
  agentName: string;
  agentType: string;
  displayOrder: number;
  provider?: string;
  model?: string;
  maxTokens?: number;
  temperature?: number;
  systemPromptOverride?: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/** Known agent types. */
export const AGENT_TYPES = ['PLANNING', 'CODING', 'REVIEW', 'QA', 'MERGE', 'COVERAGE'] as const;

export type AgentType = (typeof AGENT_TYPES)[number];

/** Limits response from GET /api/agents/squadron/limits */
export interface SquadronLimits {
  maxAgentsPerUser: number;
}
