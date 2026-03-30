-- V3: Seed test LDAP tenant and auth provider configuration
-- This migration inserts a default tenant and LDAP provider config
-- for the test OpenLDAP server (Planet Express).

-- Insert the Planet Express tenant
INSERT INTO tenants (id, name, slug, status)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Planet Express',
    'planet-express',
    'ACTIVE'
) ON CONFLICT (slug) DO NOTHING;

-- Insert the LDAP auth provider config for the Planet Express tenant
INSERT INTO auth_provider_configs (id, tenant_id, provider_type, name, enabled, priority, config)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'ldap',
    'Planet Express LDAP',
    TRUE,
    0,
    '{
        "url": "ldap://openldap-test:10389",
        "baseDn": "dc=planetexpress,dc=com",
        "userSearchBase": "ou=people",
        "userSearchFilter": "(uid={0})",
        "groupSearchBase": "ou=people",
        "groupSearchFilter": "(member={0})",
        "bindDn": "cn=admin,dc=planetexpress,dc=com",
        "bindPassword": "GoodNewsEveryone",
        "directoryType": "openldap",
        "roleMapping": {"admin_staff": "squadron-admin", "ship_crew": "developer"}
    }'::jsonb
) ON CONFLICT DO NOTHING;
