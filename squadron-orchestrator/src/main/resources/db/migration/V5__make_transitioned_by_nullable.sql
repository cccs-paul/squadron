-- V5: Make transitioned_by and triggered_by nullable
-- System-initiated workflow transitions (e.g., task sync) may not have an associated user.
ALTER TABLE task_workflows ALTER COLUMN transitioned_by DROP NOT NULL;
ALTER TABLE task_state_history ALTER COLUMN triggered_by DROP NOT NULL;
