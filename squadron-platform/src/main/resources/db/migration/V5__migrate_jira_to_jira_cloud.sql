-- Migrate legacy "JIRA" platform_type to "JIRA_CLOUD"
-- JIRA Server / Data Center connections should be re-configured as "JIRA_SERVER" by their owners.
-- Defaulting to JIRA_CLOUD since Atlassian Cloud is the more common deployment.
UPDATE platform_connections
SET platform_type = 'JIRA_CLOUD',
    updated_at = NOW()
WHERE platform_type = 'JIRA';
