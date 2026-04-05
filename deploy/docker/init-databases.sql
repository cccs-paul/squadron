-- Squadron - PostgreSQL Database Initialization
-- Creates all per-service databases on first startup
-- This script runs once when the postgres container is first initialized.

-- Create the Keycloak database
SELECT 'CREATE DATABASE keycloak OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

-- Create per-service databases
SELECT 'CREATE DATABASE squadron_identity OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_identity')\gexec

SELECT 'CREATE DATABASE squadron_config OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_config')\gexec

SELECT 'CREATE DATABASE squadron_orchestrator OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_orchestrator')\gexec

SELECT 'CREATE DATABASE squadron_platform OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_platform')\gexec

SELECT 'CREATE DATABASE squadron_agent OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_agent')\gexec

SELECT 'CREATE DATABASE squadron_workspace OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_workspace')\gexec

SELECT 'CREATE DATABASE squadron_git OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_git')\gexec

SELECT 'CREATE DATABASE squadron_review OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_review')\gexec

SELECT 'CREATE DATABASE squadron_notification OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'squadron_notification')\gexec

-- Create the Jira database (used by jira-server in testldap overlay)
SELECT 'CREATE DATABASE jira OWNER squadron'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jira')\gexec

-- Enable UUID extension on all databases
\c squadron_identity
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_config
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_orchestrator
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_platform
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_agent
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_workspace
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_git
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_review
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c squadron_notification
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c keycloak
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c jira
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
