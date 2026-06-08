-- Ghost Auto-Apply Sessions
CREATE TABLE IF NOT EXISTS ghost_apply_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id       UUID        REFERENCES resumes(id) ON DELETE SET NULL,
    job_url         TEXT        NOT NULL,
    company         VARCHAR(255),
    position        VARCHAR(255),
    status          VARCHAR(50) NOT NULL DEFAULT 'INITIALIZING',
    detected_fields JSONB,
    field_mappings  JSONB,
    execution_log   JSONB,
    screenshot_base64 TEXT,
    error_message   TEXT,
    fields_filled   INT         DEFAULT 0,
    total_fields    INT         DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ghost_apply_user ON ghost_apply_sessions(user_id);
