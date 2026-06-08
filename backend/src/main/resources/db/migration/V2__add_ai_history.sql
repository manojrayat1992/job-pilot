-- ============================================================
-- V2: AI History and Observability Tables
-- ============================================================

-- CONVERSATION HISTORY (Memory for AI interactions)
CREATE TABLE conversation_history (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id          UUID         REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id       UUID         REFERENCES resumes(id) ON DELETE CASCADE,
    role            VARCHAR(50)  NOT NULL, -- 'user', 'assistant'
    content         TEXT         NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conv_user_job ON conversation_history(user_id, job_id);

-- AI LOGS (Token tracking and cost analysis)
CREATE TABLE ai_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(50),
    model           VARCHAR(100),
    feature         VARCHAR(100), -- 'optimizer', 'cover_letter', 'parser'
    prompt_tokens   INT,
    completion_tokens INT,
    latency_ms      BIGINT,
    cost            DECIMAL(10, 6),
    status          VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_logs_user ON ai_logs(user_id);
CREATE INDEX idx_ai_logs_feature ON ai_logs(feature);

-- Add embedding column to resumes for global matching
ALTER TABLE resumes ADD COLUMN embedding vector(1536);
CREATE INDEX idx_resumes_embedding ON resumes USING hnsw (embedding vector_cosine_ops);

-- Add embedding column to jobs for global matching
ALTER TABLE jobs ADD COLUMN embedding vector(1536);
CREATE INDEX idx_jobs_embedding ON jobs USING hnsw (embedding vector_cosine_ops);
