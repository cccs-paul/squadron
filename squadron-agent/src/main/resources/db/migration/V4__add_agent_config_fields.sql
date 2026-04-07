-- V4: Enrich per-user agent configuration for multi-provider support.
-- Each agent can independently target a different hosting type and endpoint.

-- Hosting type: PLATFORM (Anthropic, OpenAI, GitHub Copilot, Cohere, Google),
--               SELF_HOSTED (Ollama, vLLM, local inference),
--               CUSTOM (any OpenAI-compatible endpoint)
ALTER TABLE user_agent_configs ADD COLUMN hosting_type VARCHAR(50) DEFAULT 'PLATFORM';

-- Base URL for self-hosted / custom endpoints (e.g. http://localhost:11434 for Ollama)
ALTER TABLE user_agent_configs ADD COLUMN base_url VARCHAR(500);

-- Reference to an encrypted API key (stored via TokenEncryptionService)
ALTER TABLE user_agent_configs ADD COLUMN api_key_ref TEXT;

-- Human-readable description auto-generated or user-set
-- e.g. "Claude Opus 4 via Anthropic" or "DeepSeek Coder v2 (local)"
ALTER TABLE user_agent_configs ADD COLUMN description VARCHAR(500);
