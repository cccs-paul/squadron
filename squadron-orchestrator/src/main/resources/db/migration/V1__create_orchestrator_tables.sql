CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    connection_id UUID,
    external_project_id VARCHAR(255),
    repo_url VARCHAR(1024),
    default_branch VARCHAR(255) DEFAULT 'main',
    branch_strategy VARCHAR(50) DEFAULT 'TRUNK_BASED',
    settings JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_tenant ON projects(tenant_id);
CREATE INDEX idx_projects_team ON projects(team_id);

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    external_id VARCHAR(255),
    external_url VARCHAR(1024),
    title VARCHAR(1024) NOT NULL,
    description TEXT,
    assignee_id UUID,
    priority VARCHAR(50),
    labels JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_tasks_tenant ON tasks(tenant_id);
CREATE INDEX idx_tasks_team ON tasks(team_id);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX idx_tasks_external ON tasks(external_id);

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID,
    name VARCHAR(255) NOT NULL,
    states JSONB NOT NULL,
    transitions JSONB NOT NULL,
    hooks JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workflow_defs_tenant ON workflow_definitions(tenant_id);

CREATE TABLE IF NOT EXISTS task_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE CASCADE,
    current_state VARCHAR(50) NOT NULL,
    previous_state VARCHAR(50),
    transition_at TIMESTAMP NOT NULL DEFAULT NOW(),
    transitioned_by UUID NOT NULL,
    metadata JSONB
);

CREATE INDEX idx_task_workflows_task ON task_workflows(task_id);
CREATE INDEX idx_task_workflows_state ON task_workflows(current_state);

CREATE TABLE IF NOT EXISTS task_state_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_workflow_id UUID NOT NULL REFERENCES task_workflows(id) ON DELETE CASCADE,
    from_state VARCHAR(50),
    to_state VARCHAR(50) NOT NULL,
    triggered_by UUID NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_history_workflow ON task_state_history(task_workflow_id);
