-- Migration: Add Interview Sessions and Enhanced History for Multi-Agent Simulation
-- Author: Antigravity

CREATE TABLE interview_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id       UUID         REFERENCES resumes(id) ON DELETE SET NULL,
    target_role     VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'COMPLETED'
    current_turn    INT          NOT NULL DEFAULT 1,
    max_turns       INT          NOT NULL DEFAULT 10,
    overall_score   INT,
    feedback_report JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Enhance conversation_history to support session-based interviews
ALTER TABLE conversation_history ADD COLUMN interview_session_id UUID REFERENCES interview_sessions(id) ON DELETE CASCADE;
ALTER TABLE conversation_history ADD COLUMN agent_persona VARCHAR(50); -- 'HIRING_MANAGER', 'TECH_LEAD', 'HR', 'SUPERVISOR'
ALTER TABLE conversation_history ADD COLUMN shadow_scores JSONB; -- Store incremental scores from agents

CREATE INDEX idx_conv_session ON conversation_history(interview_session_id);
