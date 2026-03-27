-- Add token_type column to user_platform_tokens
ALTER TABLE user_platform_tokens ADD COLUMN token_type VARCHAR(50) DEFAULT 'oauth2';

-- Add token_metadata JSONB column
ALTER TABLE user_platform_tokens ADD COLUMN token_metadata JSONB DEFAULT '{}'::jsonb;

-- Note: existing plaintext tokens should be encrypted during a data migration.
-- This migration only adds the structural changes.
-- A separate data migration should be run to encrypt existing token values.
