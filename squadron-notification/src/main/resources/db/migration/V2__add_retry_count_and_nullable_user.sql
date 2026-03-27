ALTER TABLE notifications ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0;
ALTER TABLE notifications ALTER COLUMN user_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_status_retry ON notifications(status, retry_count);
