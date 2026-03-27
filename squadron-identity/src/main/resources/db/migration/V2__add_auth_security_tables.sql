-- V2: Add authentication, security groups, and resource permissions support

-- Rename keycloak_id to external_id in users table
ALTER TABLE users RENAME COLUMN keycloak_id TO external_id;

-- Add auth_provider column to users
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(50) NOT NULL DEFAULT 'ldap';

-- Add roles column to users (JSONB array of role strings)
ALTER TABLE users ADD COLUMN roles JSONB DEFAULT '["developer"]'::jsonb;

-- Auth provider configurations per tenant
CREATE TABLE auth_provider_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_provider_configs_tenant ON auth_provider_configs(tenant_id);

-- Security groups
CREATE TABLE security_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    access_level VARCHAR(50) NOT NULL DEFAULT 'READ',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);
CREATE INDEX idx_security_groups_tenant ON security_groups(tenant_id);

-- Security group members (users or teams)
CREATE TABLE security_group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES security_groups(id) ON DELETE CASCADE,
    member_type VARCHAR(50) NOT NULL,
    member_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, member_type, member_id)
);
CREATE INDEX idx_security_group_members_group ON security_group_members(group_id);
CREATE INDEX idx_security_group_members_member ON security_group_members(member_type, member_id);

-- Resource permissions
CREATE TABLE resource_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    grantee_type VARCHAR(50) NOT NULL,
    grantee_id UUID NOT NULL,
    access_level VARCHAR(50) NOT NULL DEFAULT 'READ',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(resource_type, resource_id, grantee_type, grantee_id)
);
CREATE INDEX idx_resource_permissions_resource ON resource_permissions(tenant_id, resource_type, resource_id);
CREATE INDEX idx_resource_permissions_grantee ON resource_permissions(grantee_type, grantee_id);
