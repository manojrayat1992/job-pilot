ALTER TABLE resumes ADD COLUMN parent_resume_id UUID REFERENCES resumes(id) ON DELETE SET NULL;
ALTER TABLE resumes ADD COLUMN target_job_id UUID REFERENCES jobs(id) ON DELETE SET NULL;
ALTER TABLE resumes ADD COLUMN target_job_description TEXT;

CREATE TABLE resume_drafts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_resume_id UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    target_job_id UUID REFERENCES jobs(id) ON DELETE SET NULL,
    job_description TEXT NOT NULL,
    source_snapshot JSONB NOT NULL,
    proposed_snapshot JSONB NOT NULL,
    approved_resume_id UUID REFERENCES resumes(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_drafts_owner_source ON resume_drafts(user_id, source_resume_id, created_at DESC);
