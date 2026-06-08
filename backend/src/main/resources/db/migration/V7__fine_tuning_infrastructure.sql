-- ============================================================
-- V7: Fine-Tuning Infrastructure
-- ============================================================

-- Audit trail for AI interactions (Raw inputs/outputs for training)
ALTER TABLE ai_logs 
ADD COLUMN request_content TEXT,
ADD COLUMN response_content TEXT,
ADD COLUMN score_jump INT;

-- STORE WINNING SAMPLES (Original vs AI-Improved)
CREATE TABLE fine_tune_dataset (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ai_log_id       UUID         NOT NULL REFERENCES ai_logs(id) ON DELETE CASCADE,
    prompt_template VARCHAR(100) NOT NULL,
    input_content   TEXT         NOT NULL, -- Original bullet/context
    output_content  TEXT         NOT NULL, -- Improved bullet/result
    rating          INT          NOT NULL,
    score_jump      INT,                   -- % improvement
    is_exported     BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ft_template ON fine_tune_dataset(prompt_template);
CREATE INDEX idx_ft_exported ON fine_tune_dataset(is_exported);

-- MODEL VERSIONING (For A/B testing)
CREATE TABLE model_versions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_name    VARCHAR(50)  NOT NULL UNIQUE, -- e.g., 'v1-fine-tuned'
    model_path      VARCHAR(255) NOT NULL,        -- S3/HuggingFace URL
    is_active       BOOLEAN      DEFAULT FALSE,
    traffic_weight  INT          DEFAULT 0,       -- 0-100 (for A/B test)
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Insert initial base model
INSERT INTO model_versions (version_name, model_path, is_active, traffic_weight)
VALUES ('base-llama-3.1', 'groq/llama-3.1-8b-instant', true, 100);
