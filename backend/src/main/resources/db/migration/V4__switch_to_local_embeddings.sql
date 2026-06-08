-- 1. Drop existing indices that depend on the 768/1536 dimension
DROP INDEX IF EXISTS idx_jobs_embedding;
DROP INDEX IF EXISTS idx_resumes_embedding;
DROP INDEX IF EXISTS idx_resume_emb_hnsw;
DROP INDEX IF EXISTS idx_job_emb_hnsw;

-- 2. Alter columns to use 384 dimensions
ALTER TABLE jobs ALTER COLUMN embedding TYPE vector(384);
ALTER TABLE resumes ALTER COLUMN embedding TYPE vector(384);
ALTER TABLE resume_embeddings ALTER COLUMN embedding TYPE vector(384);
ALTER TABLE job_embeddings ALTER COLUMN embedding TYPE vector(384);

-- 3. Recreate HNSW indices for optimal performance
CREATE INDEX idx_jobs_embedding ON jobs USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_resumes_embedding ON resumes USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_resume_emb_hnsw ON resume_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_job_emb_hnsw ON job_embeddings USING hnsw (embedding vector_cosine_ops);
