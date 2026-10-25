CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revocation_reason varchar(100)
);
CREATE INDEX auth_sessions_owner ON auth_sessions(user_id);
CREATE TABLE auth_refresh_tokens (
    token_hash char(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);
CREATE INDEX auth_refresh_session ON auth_refresh_tokens(session_id);
CREATE UNIQUE INDEX auth_refresh_one_active ON auth_refresh_tokens(session_id) WHERE used_at IS NULL;
-- Legacy JWTs lack a persisted session and are rejected by the new token validator.
UPDATE refresh_tokens SET revoked=true WHERE revoked=false;
CREATE FUNCTION revoke_sessions_on_account_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash OR (OLD.enabled AND NOT NEW.enabled) THEN
        UPDATE auth_sessions SET revoked_at=now(),revocation_reason='ACCOUNT_CHANGED'
        WHERE user_id=NEW.id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER users_revoke_auth_sessions AFTER UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION revoke_sessions_on_account_change();
