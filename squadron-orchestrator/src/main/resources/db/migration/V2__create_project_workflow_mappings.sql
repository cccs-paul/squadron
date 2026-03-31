-- Maps each Squadron internal workflow state to the corresponding external
-- platform status name on a per-project basis.
-- A project may have 0..N mappings (not every internal state needs an
-- external equivalent).
CREATE TABLE IF NOT EXISTS project_workflow_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    internal_state VARCHAR(50) NOT NULL,
    external_status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_project_internal_state UNIQUE (project_id, internal_state)
);

CREATE INDEX idx_pwm_project ON project_workflow_mappings(project_id);
CREATE INDEX idx_pwm_tenant ON project_workflow_mappings(tenant_id);
