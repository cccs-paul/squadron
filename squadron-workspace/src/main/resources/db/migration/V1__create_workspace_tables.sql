CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    container_id VARCHAR(512),
    status VARCHAR(50) NOT NULL DEFAULT 'CREATING',
    repo_url VARCHAR(1024) NOT NULL,
    branch VARCHAR(255),
    base_image VARCHAR(512),
    resource_limits JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    terminated_at TIMESTAMP
);

CREATE INDEX idx_workspaces_task ON workspaces(task_id);
CREATE INDEX idx_workspaces_user ON workspaces(user_id);
CREATE INDEX idx_workspaces_tenant_status ON workspaces(tenant_id, status);
