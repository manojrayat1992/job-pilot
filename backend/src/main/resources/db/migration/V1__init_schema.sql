-- ============================================================
-- V1: Initial Schema for AI Job Pilot
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(50),
    location        VARCHAR(255),
    linkedin_url    VARCHAR(500),
    avatar_url      VARCHAR(500),
    role            VARCHAR(50)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- RESUMES
-- ============================================================
CREATE TABLE resumes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    version         INT          NOT NULL DEFAULT 1,
    is_active       BOOLEAN      NOT NULL DEFAULT FALSE,
    raw_text        TEXT,
    parsed_json     JSONB,
    file_path       VARCHAR(500),
    file_name       VARCHAR(255),
    file_type       VARCHAR(50),
    ats_score       INT,
    tags            TEXT[],
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resumes_user     ON resumes(user_id);
CREATE INDEX idx_resumes_active   ON resumes(user_id, is_active) WHERE is_active = TRUE;

-- ============================================================
-- RESUME EMBEDDINGS (pgvector)
-- ============================================================
CREATE TABLE resume_embeddings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resume_id       UUID         NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    embedding       vector(1536),
    chunk_text      TEXT,
    chunk_index     INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resume_emb_resume ON resume_embeddings(resume_id);
CREATE INDEX idx_resume_emb_hnsw   ON resume_embeddings
    USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- JOBS
-- ============================================================
CREATE TABLE jobs (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title             VARCHAR(255),
    company           VARCHAR(255),
    location          VARCHAR(255),
    source_url        VARCHAR(1000),
    raw_text          TEXT         NOT NULL,
    parsed_json       JSONB,
    experience_level  VARCHAR(50),
    job_type          VARCHAR(50),
    salary_range      VARCHAR(100),
    skills_required   TEXT[],
    skills_nice       TEXT[],
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_user ON jobs(user_id);

-- ============================================================
-- JOB EMBEDDINGS (pgvector)
-- ============================================================
CREATE TABLE job_embeddings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id          UUID         NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    embedding       vector(1536),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_emb_job  ON job_embeddings(job_id);
CREATE INDEX idx_job_emb_hnsw ON job_embeddings
    USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- APPLICATIONS (Job Tracker)
-- ============================================================
CREATE TABLE applications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id          UUID         REFERENCES jobs(id) ON DELETE SET NULL,
    resume_id       UUID         REFERENCES resumes(id) ON DELETE SET NULL,
    company         VARCHAR(255) NOT NULL,
    position        VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'SAVED',
    applied_date    DATE,
    notes           TEXT,
    cover_letter    TEXT,
    source          VARCHAR(255),
    contact_name    VARCHAR(255),
    contact_email   VARCHAR(255),
    priority        VARCHAR(20)  DEFAULT 'MEDIUM',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_apps_user   ON applications(user_id);
CREATE INDEX idx_apps_status ON applications(user_id, status);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_user  ON refresh_tokens(user_id);
