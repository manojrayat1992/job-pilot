-- ============================================================
-- V9: Add is_transient flag to resumes
-- ============================================================

ALTER TABLE resumes 
ADD COLUMN is_transient BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for filtering out transient resumes in the main hub
CREATE INDEX idx_resumes_transient ON resumes(user_id, is_transient);
