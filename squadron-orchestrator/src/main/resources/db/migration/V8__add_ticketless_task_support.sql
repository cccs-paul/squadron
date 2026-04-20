-- V8: Add ticketless task support
-- Ticketless tasks are created directly from the UI without an external ticket

-- Make project_id nullable (ticketless tasks may not belong to a project)
ALTER TABLE tasks ALTER COLUMN project_id DROP NOT NULL;

-- Add ticketless-specific columns
ALTER TABLE tasks ADD COLUMN ticketless BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN ticketless_status VARCHAR(50);
ALTER TABLE tasks ADD COLUMN branch_name VARCHAR(500);
ALTER TABLE tasks ADD COLUMN create_branch BOOLEAN DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN agent_mode VARCHAR(50);
ALTER TABLE tasks ADD COLUMN agent_config_id UUID;
ALTER TABLE tasks ADD COLUMN prompt TEXT;

-- Index for querying ticketless tasks by tenant
CREATE INDEX idx_tasks_ticketless_tenant ON tasks (tenant_id, ticketless) WHERE ticketless = TRUE;
