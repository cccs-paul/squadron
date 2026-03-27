CREATE TABLE IF NOT EXISTS config_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID,
    user_id UUID,
    config_key VARCHAR(255) NOT NULL,
    config_value JSONB NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    UNIQUE(tenant_id, team_id, user_id, config_key)
);

CREATE INDEX idx_config_entries_tenant ON config_entries(tenant_id);
CREATE INDEX idx_config_entries_key ON config_entries(config_key);
CREATE INDEX idx_config_entries_lookup ON config_entries(tenant_id, team_id, user_id, config_key);

CREATE TABLE IF NOT EXISTS config_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    config_entry_id UUID NOT NULL REFERENCES config_entries(id) ON DELETE CASCADE,
    config_key VARCHAR(255) NOT NULL,
    previous_value JSONB,
    new_value JSONB NOT NULL,
    changed_by UUID,
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_config_audit_entry ON config_audit_log(config_entry_id);
