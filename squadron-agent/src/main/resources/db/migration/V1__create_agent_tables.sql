CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    agent_type VARCHAR(50) NOT NULL,
    provider VARCHAR(100),
    model VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    total_tokens BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_task ON conversations(task_id);
CREATE INDEX idx_conversations_user ON conversations(user_id);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls JSONB,
    token_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conv_messages_conversation ON conversation_messages(conversation_id);

CREATE TABLE IF NOT EXISTS task_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    conversation_id UUID REFERENCES conversations(id),
    version INTEGER NOT NULL DEFAULT 1,
    plan_content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approved_by UUID,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_plans_task ON task_plans(task_id);

CREATE TABLE IF NOT EXISTS squadron_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID,
    user_id UUID,
    name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_squadron_configs_tenant ON squadron_configs(tenant_id);
CREATE INDEX idx_squadron_configs_lookup ON squadron_configs(tenant_id, team_id, user_id);
