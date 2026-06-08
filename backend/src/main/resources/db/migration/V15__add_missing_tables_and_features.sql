-- ============================================================
-- V2: Add all missing tables + new feature tables
-- ============================================================

-- ============================================================
-- INTERVIEW SESSIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id       UUID         REFERENCES resumes(id) ON DELETE SET NULL,
    target_role     VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    current_turn    INT          NOT NULL DEFAULT 1,
    max_turns       INT          NOT NULL DEFAULT 10,
    overall_score   INT,
    feedback_report JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interview_user ON interview_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_interview_status ON interview_sessions(user_id, status);

-- ============================================================
-- CONVERSATION HISTORY
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation_history (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id               UUID         REFERENCES jobs(id) ON DELETE SET NULL,
    resume_id            UUID         REFERENCES resumes(id) ON DELETE SET NULL,
    interview_session_id UUID         REFERENCES interview_sessions(id) ON DELETE CASCADE,
    role                 VARCHAR(50)  NOT NULL,
    agent_persona        VARCHAR(50),
    content              TEXT         NOT NULL,
    metadata             JSONB,
    shadow_scores        JSONB,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_user ON conversation_history(user_id);
CREATE INDEX IF NOT EXISTS idx_conv_session ON conversation_history(interview_session_id);
CREATE INDEX IF NOT EXISTS idx_conv_job ON conversation_history(user_id, job_id);

-- ============================================================
-- AI LOGS (Usage Tracking + Feedback + Fine-Tune Data)
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_logs (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(100),
    model             VARCHAR(100),
    feature           VARCHAR(100),
    prompt_template   VARCHAR(255),
    prompt_tokens     INT,
    completion_tokens INT,
    latency_ms        BIGINT,
    cost              DECIMAL(10, 6),
    status            VARCHAR(50),
    rating            INT,
    feedback_comment  TEXT,
    request_content   TEXT,
    response_content  TEXT,
    score_jump        INT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_logs_user ON ai_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_logs_feature ON ai_logs(feature);
CREATE INDEX IF NOT EXISTS idx_ai_logs_template ON ai_logs(prompt_template);

-- ============================================================
-- MODEL VERSIONS (A/B Testing)
-- ============================================================
CREATE TABLE IF NOT EXISTS model_versions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_name   VARCHAR(255) NOT NULL UNIQUE,
    model_path     VARCHAR(500) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT FALSE,
    traffic_weight INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Insert default model version
INSERT INTO model_versions (version_name, model_path, is_active, traffic_weight)
VALUES ('gpt-4o-mini-default', 'gpt-4o-mini', TRUE, 100)
ON CONFLICT (version_name) DO NOTHING;

-- ============================================================
-- FINE TUNE DATASET
-- ============================================================
CREATE TABLE IF NOT EXISTS fine_tune_dataset (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ai_log_id       UUID         NOT NULL REFERENCES ai_logs(id) ON DELETE CASCADE,
    prompt_template VARCHAR(255) NOT NULL,
    input_content   TEXT         NOT NULL,
    output_content  TEXT         NOT NULL,
    rating          INT,
    score_jump      INT,
    is_exported     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- HIRING MANAGERS (Networking CRM)
-- ============================================================
CREATE TABLE IF NOT EXISTS hiring_managers (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    job_title        VARCHAR(255),
    company_name     VARCHAR(255),
    company_domain   VARCHAR(255),
    email            VARCHAR(255),
    linkedin_url     VARCHAR(500),
    function         VARCHAR(255),
    management_level VARCHAR(100),
    country          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hiring_mgr_company ON hiring_managers(company_name);

-- ============================================================
-- Add credits and onboarding columns to users (if missing)
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS credits INT NOT NULL DEFAULT 5;
ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS work_mode VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS target_role VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS current_org VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS target_salary VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token_expiry TIMESTAMP;

-- Add ats_issues column to resumes
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS ats_issues JSONB;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS is_transient BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- EMAIL DRIP SEQUENCES (New Feature)
-- ============================================================
CREATE TABLE IF NOT EXISTS email_sequences (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_company  VARCHAR(255) NOT NULL,
    target_role     VARCHAR(255),
    contact_name    VARCHAR(255),
    contact_email   VARCHAR(255),
    status          VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS email_sequence_steps (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sequence_id     UUID         NOT NULL REFERENCES email_sequences(id) ON DELETE CASCADE,
    step_number     INT          NOT NULL,
    day_offset      INT          NOT NULL DEFAULT 0,
    channel         VARCHAR(50)  NOT NULL DEFAULT 'EMAIL',
    subject         VARCHAR(500),
    body            TEXT         NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    scheduled_at    TIMESTAMP,
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_seq_user ON email_sequences(user_id);
CREATE INDEX IF NOT EXISTS idx_email_steps_seq ON email_sequence_steps(sequence_id);

-- ============================================================
-- CULTURE FIT ASSESSMENTS (New Feature)
-- ============================================================
CREATE TABLE IF NOT EXISTS culture_fit_assessments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    overall_score   INT,
    analysis        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_culture_fit_user ON culture_fit_assessments(user_id);

-- ============================================================
-- SALARY NEGOTIATIONS (New Feature)
-- ============================================================
CREATE TABLE IF NOT EXISTS salary_negotiations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name    VARCHAR(255),
    role            VARCHAR(255),
    offer_amount    DECIMAL(12, 2),
    currency        VARCHAR(10)  DEFAULT 'USD',
    analysis        JSONB,
    counter_offer   JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_salary_neg_user ON salary_negotiations(user_id);
