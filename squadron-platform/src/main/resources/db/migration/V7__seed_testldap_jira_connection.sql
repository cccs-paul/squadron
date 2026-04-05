-- V7: Seed a Jira Server platform connection for the test LDAP tenant.
--
-- This connection points to the Jira Server instance started by the
-- docker-compose-testldap.yml overlay.  After Jira's first-time setup
-- wizard completes, the user should:
--
--   1. Create a Personal Access Token (PAT) in Jira
--      (Profile → Personal Access Tokens → Create token)
--   2. Update this connection's credentials via the Squadron UI
--      (Settings → Providers & Projects → edit the "Jira Server (Test)" connection)
--
-- The placeholder PAT below ("REPLACE_ME_WITH_JIRA_PAT") will cause auth
-- failures until replaced with a real token — this is intentional.

INSERT INTO platform_connections (
    id, tenant_id, platform_type, name, base_url, auth_type,
    credentials, status, platform_category, metadata
)
VALUES (
    'c0000000-0000-0000-0000-000000000001',            -- stable id
    'a0000000-0000-0000-0000-000000000001',            -- Planet Express tenant
    'JIRA_SERVER',
    'Jira Server (Test)',
    'http://jira-server:8090',                          -- Docker internal URL
    'PAT',
    '{"personalAccessToken": "REPLACE_ME_WITH_JIRA_PAT"}'::jsonb,
    'ACTIVE',
    'TICKET_PROVIDER',
    '{"description": "Test Jira Server instance running in Docker. Replace the PAT after completing Jira setup."}'::jsonb
) ON CONFLICT (id) DO NOTHING;
