-- V8: Seed a GitLab CE platform connection for the test LDAP tenant.
--
-- This connection points to the GitLab CE instance started by the
-- docker-compose-testldap.yml overlay.  After GitLab's first boot
-- completes, the user should:
--
--   1. Log in as root (retrieve password with:
--      docker exec squadron-gitlab-ce cat /etc/gitlab/initial_root_password)
--   2. Create a Personal Access Token (PAT) with api scope
--      (Preferences → Access Tokens → Add new token)
--   3. Update this connection's credentials via the Squadron UI
--      (Settings → Providers & Projects → edit the "GitLab CE (Test)" connection)
--
-- The placeholder PAT below ("REPLACE_ME_WITH_GITLAB_PAT") will cause auth
-- failures until replaced with a real token — this is intentional.

INSERT INTO platform_connections (
    id, tenant_id, platform_type, name, base_url, auth_type,
    credentials, status, platform_category, metadata
)
VALUES (
    'c0000000-0000-0000-0000-000000000002',            -- stable id
    'a0000000-0000-0000-0000-000000000001',            -- Planet Express tenant
    'GITLAB',
    'GitLab CE (Test)',
    'http://gitlab-ce:80',                              -- Docker internal URL
    'PAT',
    '{"pat": "REPLACE_ME_WITH_GITLAB_PAT"}'::jsonb,
    'ACTIVE',
    'GIT_REMOTE',
    '{"description": "Test GitLab CE instance running in Docker. Replace the PAT after completing GitLab setup.", "sshUrl": "ssh://git@gitlab-ce:22"}'::jsonb
) ON CONFLICT (id) DO NOTHING;
