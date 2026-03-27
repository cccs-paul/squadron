CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    reviewer_id UUID,
    reviewer_type VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reviews_task ON reviews(task_id);
CREATE INDEX idx_reviews_reviewer ON reviews(reviewer_id);

CREATE TABLE IF NOT EXISTS review_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    file_path VARCHAR(1024),
    line_number INTEGER,
    body TEXT NOT NULL,
    severity VARCHAR(20),
    category VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_comments_review ON review_comments(review_id);

CREATE TABLE IF NOT EXISTS review_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    team_id UUID,
    min_human_approvals INTEGER NOT NULL DEFAULT 1,
    require_ai_review BOOLEAN NOT NULL DEFAULT TRUE,
    self_review_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    auto_request_reviewers JSONB,
    review_checklist JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_policies_tenant ON review_policies(tenant_id);
CREATE UNIQUE INDEX idx_review_policies_unique ON review_policies(tenant_id, COALESCE(team_id, '00000000-0000-0000-0000-000000000000'));
