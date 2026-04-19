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
  // ── Cloud / Platform providers ──────────────────────────────
  {
    id: 'github-copilot',
    label: 'GitHub Copilot',
    hostingType: 'PLATFORM',
    models: [
      { id: 'claude-opus-4', label: 'Claude Opus 4' },
      { id: 'claude-opus-4.6', label: 'Claude Opus 4.6' },
      { id: 'claude-sonnet-4', label: 'Claude Sonnet 4' },
      { id: 'gpt-4.1', label: 'GPT-4.1' },
      { id: 'gpt-4.1-mini', label: 'GPT-4.1 Mini' },
      { id: 'gpt-4o', label: 'GPT-4o' },
      { id: 'o3', label: 'o3' },
      { id: 'o4-mini', label: 'o4-mini' },
      { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
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
      { id: 'claude-opus-4.6', label: 'Claude Opus 4.6' },
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
      { id: 'gpt-4.1', label: 'GPT-4.1' },
      { id: 'gpt-4.1-mini', label: 'GPT-4.1 Mini' },
      { id: 'gpt-4.1-nano', label: 'GPT-4.1 Nano' },
      { id: 'gpt-4o', label: 'GPT-4o' },
      { id: 'gpt-4o-mini', label: 'GPT-4o Mini' },
      { id: 'o3', label: 'o3' },
      { id: 'o3-mini', label: 'o3 Mini' },
      { id: 'o4-mini', label: 'o4-mini' },
      { id: 'codex-mini', label: 'Codex Mini' },
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
      { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
      { id: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash' },
      { id: 'gemma-4', label: 'Gemma 4' },
    ],
  },
  {
    id: 'mistral',
    label: 'Mistral AI',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.mistral.ai/v1',
    models: [
      { id: 'mistral-large-latest', label: 'Mistral Large' },
      { id: 'mistral-medium-latest', label: 'Mistral Medium' },
      { id: 'mistral-small-latest', label: 'Mistral Small' },
      { id: 'codestral-latest', label: 'Codestral' },
      { id: 'devstral-small-latest', label: 'Devstral Small' },
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
      { id: 'command-a-reasoning-08-2025', label: 'Command A Reasoning' },
      { id: 'command-a-vision-07-2025', label: 'Command A Vision' },
      { id: 'command-r7b-12-2024', label: 'Command R7B' },
      { id: 'command-r-plus-08-2024', label: 'Command R+ (08-2024)' },
      { id: 'command-r-08-2024', label: 'Command R (08-2024)' },
    ],
  },
  {
    id: 'deepseek',
    label: 'DeepSeek',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.deepseek.com/v1',
    models: [
      { id: 'deepseek-chat', label: 'DeepSeek V3' },
      { id: 'deepseek-reasoner', label: 'DeepSeek R1' },
      { id: 'deepseek-coder', label: 'DeepSeek Coder' },
    ],
  },
  {
    id: 'xai',
    label: 'xAI',
    hostingType: 'PLATFORM',
    requiresApiKey: true,
    defaultBaseUrl: 'https://api.x.ai/v1',
    models: [
      { id: 'grok-3', label: 'Grok 3' },
      { id: 'grok-3-mini', label: 'Grok 3 Mini' },
    ],
  },
  // ── Self-hosted / Air-gapped providers ──────────────────────
  {
    id: 'ollama',
    label: 'Ollama',
    hostingType: 'SELF_HOSTED',
    defaultBaseUrl: 'http://localhost:11434',
    models: [
      // CPU-friendly (run without GPU)
      { id: 'qwen2.5-coder:1.5b', label: 'Qwen 2.5 Coder 1.5B (CPU)' },
      { id: 'gemma4:e2b', label: 'Gemma 4 E2B' },
      { id: 'phi-4-mini', label: 'Phi-4 Mini (CPU)' },
      // GPU recommended
      { id: 'qwen2.5-coder:7b', label: 'Qwen 2.5 Coder 7B' },
      { id: 'qwen2.5-coder:14b', label: 'Qwen 2.5 Coder 14B' },
      { id: 'qwen2.5-coder:32b', label: 'Qwen 2.5 Coder 32B' },
      { id: 'deepseek-coder-v2:16b', label: 'DeepSeek Coder v2 16B' },
      { id: 'deepseek-r1:8b', label: 'DeepSeek R1 8B' },
      { id: 'deepseek-r1:14b', label: 'DeepSeek R1 14B' },
      { id: 'codestral:latest', label: 'Codestral' },
      { id: 'devstral:latest', label: 'Devstral' },
      { id: 'llama3.3:latest', label: 'Llama 3.3 70B' },
      { id: 'llama3.1:8b', label: 'Llama 3.1 8B' },
      { id: 'gemma4:e4b', label: 'Gemma 4 E4B' },
      { id: 'gemma4:26b', label: 'Gemma 4 26B' },
      { id: 'mistral:latest', label: 'Mistral 7B' },
      { id: 'phi-4:latest', label: 'Phi-4 14B' },
      { id: 'gemma3:4b', label: 'Gemma 3 4B' },
      { id: 'starcoder2:7b', label: 'StarCoder2 7B' },
    ],
  },
  {
    id: 'openai-compatible',
    label: 'OpenAI-Compatible (Custom)',
    hostingType: 'CUSTOM',
    requiresApiKey: true,
    defaultBaseUrl: 'http://localhost:8080/v1',
    models: [
      // Users type their own model ID; these are common examples
      { id: 'default', label: 'Default' },
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
