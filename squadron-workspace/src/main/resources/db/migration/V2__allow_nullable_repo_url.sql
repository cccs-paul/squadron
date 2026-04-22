-- Allow workspaces without a repo_url (ephemeral sandbox containers don't clone a repo)
ALTER TABLE workspaces ALTER COLUMN repo_url DROP NOT NULL;
