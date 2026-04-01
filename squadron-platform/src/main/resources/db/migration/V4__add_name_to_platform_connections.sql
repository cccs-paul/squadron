-- Add a human-readable name column to platform_connections
-- Allows users to label their provider connections (e.g. "Jira Cloud - Production")

ALTER TABLE platform_connections ADD COLUMN name VARCHAR(255);

-- Backfill existing rows with a generated name based on platform_type
UPDATE platform_connections SET name = platform_type || ' Connection' WHERE name IS NULL;

-- Make name NOT NULL after backfill
ALTER TABLE platform_connections ALTER COLUMN name SET NOT NULL;
