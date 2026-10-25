-- Separate spendable balances prevent stale JPA profile saves from restoring credits.
CREATE TABLE ai_credit_accounts (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    available INTEGER NOT NULL CHECK (available >= 0)
);
INSERT INTO ai_credit_accounts(user_id, available) SELECT id, greatest(coalesce(credits, 0), 0) FROM users;
CREATE FUNCTION initialize_ai_credit_account() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO ai_credit_accounts(user_id, available) VALUES(NEW.id, greatest(coalesce(NEW.credits, 0), 0));
    RETURN NEW;
END;
$$;
CREATE TRIGGER users_initialize_ai_credits AFTER INSERT ON users
FOR EACH ROW EXECUTE FUNCTION initialize_ai_credit_account();

CREATE TABLE ai_credit_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_key VARCHAR(200) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    feature VARCHAR(100) NOT NULL,
    requested_model VARCHAR(200) NOT NULL,
    model VARCHAR(200),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED')),
    credits INTEGER NOT NULL CHECK (credits IN (0, 1)),
    cached_content TEXT,
    prompt_tokens BIGINT CHECK (prompt_tokens >= 0),
    completion_tokens BIGINT CHECK (completion_tokens >= 0),
    duration_ms BIGINT CHECK (duration_ms >= 0),
    resolution VARCHAR(500),
    reconciled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(user_id, operation_key, attempt)
);
CREATE INDEX ai_credit_ledger_user_time ON ai_credit_ledger(user_id, created_at DESC);
CREATE INDEX ai_credit_ledger_pending ON ai_credit_ledger(created_at) WHERE status='RESERVED';
CREATE TABLE ai_credit_grants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reference VARCHAR(200) NOT NULL,
    amount INTEGER NOT NULL CHECK(amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id, reference)
);
