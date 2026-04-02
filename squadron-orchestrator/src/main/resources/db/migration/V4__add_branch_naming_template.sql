-- V4: Add branch_naming_template column to projects table

ALTER TABLE projects ADD COLUMN branch_naming_template VARCHAR(500);

-- Set a sensible default for existing projects
UPDATE projects SET branch_naming_template = '{strategy}/{ticket}-{description}'
WHERE branch_naming_template IS NULL;
