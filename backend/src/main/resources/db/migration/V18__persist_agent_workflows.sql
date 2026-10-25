-- Product history and step checkpoints. No external API calls run inside these transactions.
CREATE TABLE agent_workflow_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    workflow_type VARCHAR(40) NOT NULL,
    definition_version INT NOT NULL DEFAULT 1,
    request_json JSONB NOT NULL,
    context_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    execution_token UUID,
    lease_until TIMESTAMPTZ,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, request_id)
);
CREATE INDEX idx_agent_workflows_user_created ON agent_workflow_runs(user_id, created_at DESC, id);
CREATE TABLE agent_workflow_steps (
    run_id UUID NOT NULL REFERENCES agent_workflow_runs(id) ON DELETE CASCADE,
    step_key VARCHAR(60) NOT NULL,
    position INT NOT NULL,
    agent VARCHAR(30) NOT NULL,
    action VARCHAR(150) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CHAT','SEARCH')),
    artifact_key VARCHAR(60) NOT NULL,
    system_prompt TEXT NOT NULL,
    prompt_template TEXT NOT NULL,
    resolved_prompt TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    output_json JSONB NOT NULL DEFAULT '{}',
    model VARCHAR(255),
    error TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (run_id, step_key),
    UNIQUE (run_id, position)
);
