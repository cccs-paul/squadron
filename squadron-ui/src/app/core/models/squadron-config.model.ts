/**
 * User agent squadron configuration model.
 * Mirrors com.squadron.agent.entity.UserAgentConfig / UserAgentConfigDto.
 * Agents are general-purpose and can be configured with different AI providers/models.
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

/** Limits response from GET /api/agents/squadron/limits */
export interface SquadronLimits {
  maxAgentsPerUser: number;
}
