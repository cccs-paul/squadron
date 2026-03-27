CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    related_task_id UUID,
    event_type VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP,
    read_at TIMESTAMP
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_user_status ON notifications(user_id, status);
CREATE INDEX idx_notifications_status ON notifications(status);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    enable_email BOOLEAN NOT NULL DEFAULT TRUE,
    enable_slack BOOLEAN NOT NULL DEFAULT FALSE,
    enable_teams BOOLEAN NOT NULL DEFAULT FALSE,
    enable_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    slack_webhook_url VARCHAR(1024),
    teams_webhook_url VARCHAR(1024),
    email_address VARCHAR(255),
    muted_event_types JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_prefs_user ON notification_preferences(user_id);
