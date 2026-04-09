ALTER TABLE projects ADD COLUMN git_connection_id UUID;
ALTER TABLE projects ADD COLUMN clone_url VARCHAR(1000);
