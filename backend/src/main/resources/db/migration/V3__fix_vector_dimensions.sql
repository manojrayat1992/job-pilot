-- ============================================================
-- V3: Fix Vector Dimensions for Groq Compatibility
-- Groq's nomic-embed-text-v1.5 uses 768 dimensions.
-- ============================================================

-- Drop existing HNSW indices first
DROP INDEX IF EXISTS idx_resumes_embedding;
DROP INDEX IF EXISTS idx_jobs_embedding;
DROP INDEX IF EXISTS idx_resume_emb_hnsw;
DROP INDEX IF EXISTS idx_job_emb_hnsw;

-- Alter column types to 768 dimensions
ALTER TABLE resumes ALTER COLUMN embedding TYPE vector(768);
ALTER TABLE jobs ALTER COLUMN embedding TYPE vector(768);
ALTER TABLE resume_embeddings ALTER COLUMN embedding TYPE vector(768);
ALTER TABLE job_embeddings ALTER COLUMN embedding TYPE vector(768);

-- Recreate indices
CREATE INDEX idx_resumes_embedding ON resumes USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_jobs_embedding ON jobs USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_resume_emb_hnsw ON resume_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_job_emb_hnsw ON job_embeddings USING hnsw (embedding vector_cosine_ops);
