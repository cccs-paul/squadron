-- Make team_id optional on projects and tasks tables
-- Team assignment can be done later when team management is configured

ALTER TABLE projects ALTER COLUMN team_id DROP NOT NULL;

ALTER TABLE tasks ALTER COLUMN team_id DROP NOT NULL;
