-- ============================================================
-- V6: Add Feedback and Template Columns to AI Logs
-- ============================================================

ALTER TABLE ai_logs 
ADD COLUMN rating INT,
ADD COLUMN feedback_comment TEXT,
ADD COLUMN prompt_template VARCHAR(100);

-- Create indexes to help with analytical queries
CREATE INDEX idx_ai_logs_rating ON ai_logs(rating);
CREATE INDEX idx_ai_logs_template ON ai_logs(prompt_template);
