CREATE TABLE branch_strategies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    project_id UUID,
    strategy_type VARCHAR(50) NOT NULL,
    branch_prefix VARCHAR(100) DEFAULT 'squadron/',
    target_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    development_branch VARCHAR(100),
    branch_name_template VARCHAR(255) DEFAULT '{prefix}{taskId}/{slug}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, project_id)
);

CREATE INDEX idx_branch_strategies_tenant ON branch_strategies(tenant_id);
