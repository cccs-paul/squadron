CREATE TABLE IF NOT EXISTS platform_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    platform_type VARCHAR(50) NOT NULL,
    base_url VARCHAR(1024) NOT NULL,
    auth_type VARCHAR(50) NOT NULL,
    credentials JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_conn_tenant ON platform_connections(tenant_id);

CREATE TABLE IF NOT EXISTS user_platform_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES platform_connections(id) ON DELETE CASCADE,
    access_token VARCHAR(2048) NOT NULL,
    refresh_token VARCHAR(2048),
    expires_at TIMESTAMP,
    scopes VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, connection_id)
);

CREATE INDEX idx_user_tokens_user ON user_platform_tokens(user_id);
CREATE INDEX idx_user_tokens_connection ON user_platform_tokens(connection_id);
