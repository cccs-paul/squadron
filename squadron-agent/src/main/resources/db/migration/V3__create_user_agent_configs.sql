-- V3: Per-user agent squadron configuration
-- Each user gets a configurable set of AI agents (default 8).
-- Agent names are unique per user within a tenant.

CREATE TABLE user_agent_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    agent_name      VARCHAR(100) NOT NULL,
    agent_type      VARCHAR(50) NOT NULL,
    display_order   INTEGER NOT NULL DEFAULT 0,
    provider        VARCHAR(100),
    model           VARCHAR(200),
    max_tokens      INTEGER,
    temperature     DOUBLE PRECISION,
    system_prompt_override TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Unique constraint: agent name must be unique per user within a tenant
ALTER TABLE user_agent_configs
    ADD CONSTRAINT uk_user_agent_name UNIQUE (tenant_id, user_id, agent_name);

-- Index for fast lookups by user
CREATE INDEX idx_user_agent_configs_tenant_user ON user_agent_configs (tenant_id, user_id);

-- Index for ordering
CREATE INDEX idx_user_agent_configs_order ON user_agent_configs (tenant_id, user_id, display_order);
