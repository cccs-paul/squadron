-- V6: Add SSH keys table and platform_category column to platform_connections

-- Add platform_category to platform_connections
ALTER TABLE platform_connections ADD COLUMN platform_category VARCHAR(50);

-- Backfill: classify existing connections by platform type
UPDATE platform_connections SET platform_category = 'TICKET_PROVIDER'
WHERE platform_type IN ('JIRA_CLOUD', 'JIRA_SERVER', 'AZURE_DEVOPS');

UPDATE platform_connections SET platform_category = 'GIT_REMOTE'
WHERE platform_type IN ('GITHUB', 'GITLAB', 'BITBUCKET');

-- Set NOT NULL after backfill (default to TICKET_PROVIDER for any unknown)
UPDATE platform_connections SET platform_category = 'TICKET_PROVIDER'
WHERE platform_category IS NULL;

ALTER TABLE platform_connections ALTER COLUMN platform_category SET NOT NULL;

-- Create SSH keys table
CREATE TABLE ssh_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES platform_connections(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    private_key TEXT NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    key_type VARCHAR(50) NOT NULL DEFAULT 'ED25519',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for SSH keys
CREATE INDEX idx_ssh_keys_tenant_id ON ssh_keys(tenant_id);
CREATE INDEX idx_ssh_keys_connection_id ON ssh_keys(connection_id);
CREATE INDEX idx_ssh_keys_fingerprint ON ssh_keys(fingerprint);

-- Index for platform_category
CREATE INDEX idx_platform_connections_category ON platform_connections(platform_category);
CREATE INDEX idx_platform_connections_tenant_category ON platform_connections(tenant_id, platform_category);
