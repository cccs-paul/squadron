/**
 * User agent squadron configuration model.
 * Mirrors com.squadron.agent.entity.UserAgentConfig / UserAgentConfigDto.
 * Agents are general-purpose and independently configurable with different
 * AI providers, hosting types, and models.
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
  hostingType?: HostingType;
  baseUrl?: string;
  apiKeyRef?: string;
  description?: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/** Hosting types for agent configuration. */
export type HostingType = 'PLATFORM' | 'SELF_HOSTED' | 'CUSTOM';

/** Limits response from GET /api/agents/squadron/limits */
export interface SquadronLimits {
  maxAgentsPerUser: number;
}

/** A known provider entry for the UI catalog. */
export interface ProviderCatalogEntry {
  id: string;
  label: string;
  hostingType: HostingType;
  models: ModelCatalogEntry[];
  /** Whether this provider requires the user to supply an API key. */
  requiresApiKey?: boolean;
  /** Default base URL hint shown as placeholder (optional override). */
  defaultBaseUrl?: string;
}

/** A known model entry within a provider. */
export interface ModelCatalogEntry {
  id: string;
  label: string;
}

/**
 * Known provider/model catalogs for UI dropdowns.
 * Each agent can independently choose any of these.
 */
export const PROVIDER_CATALOG: ProviderCatalogEntry[] = [
  {
    id: 'github-copilot',
    label: 'GitHub Copilot',
    hostingType: 'PLATFORM',
    models: [
      { id: 'claude-sonnet-4', label: 'Claude Sonnet 4' },
      { id: 'gpt-4o', label: 'GPT-4o' },
      { id: 'o3', label: 'o3' },
      { id: 'o4-mini', label: 'o4-mini' },
    ],
  },
  {
    id: 'anthropic',
    label: 'Anthropic',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.anthropic.com',
    models: [
      { id: 'claude-opus-4', label: 'Claude Opus 4' },
      { id: 'claude-sonnet-4', label: 'Claude Sonnet 4' },
      { id: 'claude-haiku-3.5', label: 'Claude Haiku 3.5' },
    ],
  },
  {
    id: 'openai',
    label: 'OpenAI',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.openai.com/v1',
    models: [
      { id: 'gpt-4o', label: 'GPT-4o' },
      { id: 'o3', label: 'o3' },
      { id: 'o4-mini', label: 'o4-mini' },
    ],
  },
  {
    id: 'google',
    label: 'Google',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://generativelanguage.googleapis.com',
    models: [
      { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
      { id: 'gemma-4', label: 'Gemma 4' },
    ],
  },
  {
    id: 'cohere',
    label: 'Cohere',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.cohere.com/v2',
    models: [
      { id: 'command-a-03-2025', label: 'Command A' },
    ],
  },
  {
    id: 'ollama',
    label: 'Ollama',
    hostingType: 'SELF_HOSTED',
    defaultBaseUrl: 'http://localhost:11434',
    models: [
      { id: 'llama3.3', label: 'Llama 3.3' },
      { id: 'deepseek-coder-v2', label: 'DeepSeek Coder v2' },
      { id: 'codestral', label: 'Codestral' },
      { id: 'qwen2.5-coder', label: 'Qwen 2.5 Coder' },
      { id: 'gemma4', label: 'Gemma 4' },
    ],
  },
];

/** Generates a human-readable description from provider + model + hosting type. */
export function generateAgentDescription(
  provider?: string,
  model?: string,
  hostingType?: HostingType,
): string {
  if (!model) return provider ?? 'Unconfigured';
  const providerEntry = PROVIDER_CATALOG.find(p => p.id === provider);
  const modelEntry = providerEntry?.models.find(m => m.id === model);
  const displayModel = modelEntry?.label ?? model;
  if (hostingType === 'SELF_HOSTED') return `${displayModel} (local)`;
  if (hostingType === 'CUSTOM') return `${displayModel} via Custom endpoint`;
  const displayProvider = providerEntry?.label ?? provider;
  return displayProvider ? `${displayModel} via ${displayProvider}` : displayModel;
}
