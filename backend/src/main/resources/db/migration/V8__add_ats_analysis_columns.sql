-- ============================================================
-- V8: ATS Analysis Columns for Resumes
-- ============================================================

-- ats_score already exists in V1
ALTER TABLE resumes 
ADD COLUMN ats_issues JSONB;

-- Create an index for searching/filtering by score (if not already there)
-- V1 has it as INT but no specific index. 
-- We'll add it here for performance.
CREATE INDEX IF NOT EXISTS idx_resumes_ats_score ON resumes(ats_score);
