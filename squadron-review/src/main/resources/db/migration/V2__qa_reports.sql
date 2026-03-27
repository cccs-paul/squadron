-- QA Reports table
CREATE TABLE qa_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    verdict VARCHAR(50) NOT NULL,
    summary TEXT,
    line_coverage DOUBLE PRECISION,
    branch_coverage DOUBLE PRECISION,
    tests_passed INTEGER,
    tests_failed INTEGER,
    tests_skipped INTEGER,
    findings JSONB,
    test_gaps JSONB,
    coverage_details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qa_reports_task ON qa_reports(task_id);
CREATE INDEX idx_qa_reports_tenant ON qa_reports(tenant_id);
