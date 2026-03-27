CREATE TABLE IF NOT EXISTS git_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    details JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_git_ops_task ON git_operations(task_id);
CREATE INDEX idx_git_ops_workspace ON git_operations(workspace_id);

CREATE TABLE IF NOT EXISTS pull_request_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL,
    external_pr_id VARCHAR(255) NOT NULL,
    external_pr_url VARCHAR(1024),
    title VARCHAR(1024) NOT NULL,
    source_branch VARCHAR(255) NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pr_records_task ON pull_request_records(task_id);
CREATE INDEX idx_pr_records_tenant_status ON pull_request_records(tenant_id, status);
