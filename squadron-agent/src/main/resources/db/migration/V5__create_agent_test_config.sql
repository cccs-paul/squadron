-- V5: Agent test configuration - stores per-tenant settings for the test data generator model
-- used when testing agents from the "My Agent Squadron" UI.

CREATE TABLE IF NOT EXISTS agent_test_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    generator_provider  VARCHAR(100)  NOT NULL DEFAULT 'ollama',
    generator_model     VARCHAR(200)  NOT NULL DEFAULT 'gemma4:e2b',
    generator_hosting_type VARCHAR(50) NOT NULL DEFAULT 'SELF_HOSTED',
    generator_base_url  VARCHAR(500),
    generator_api_key   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_test_configs_tenant_user
    ON agent_test_configs (tenant_id, user_id);
