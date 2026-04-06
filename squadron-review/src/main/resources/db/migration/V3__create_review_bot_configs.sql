CREATE TABLE review_bot_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    bot_username VARCHAR(255) NOT NULL,
    bot_access_token VARCHAR(2048) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_assign BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_bot_tenant_connection UNIQUE (tenant_id, connection_id)
);

CREATE INDEX idx_review_bot_configs_tenant ON review_bot_configs(tenant_id);
