ALTER TABLE platform_connections ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(512);
