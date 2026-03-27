CREATE TABLE IF NOT EXISTS token_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID,
    user_id UUID,
    task_id UUID,
    agent_type VARCHAR(50) NOT NULL,
    model_name VARCHAR(100),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_usages_tenant ON token_usages(tenant_id);
CREATE INDEX idx_token_usages_tenant_user ON token_usages(tenant_id, user_id);
CREATE INDEX idx_token_usages_tenant_team ON token_usages(tenant_id, team_id);
CREATE INDEX idx_token_usages_tenant_created ON token_usages(tenant_id, created_at);
