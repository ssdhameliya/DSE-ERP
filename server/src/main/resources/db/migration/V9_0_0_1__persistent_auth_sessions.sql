CREATE TABLE IF NOT EXISTS auth_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id INTEGER NOT NULL,
    username VARCHAR(160) NOT NULL,
    role_code VARCHAR(80) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_session_user_id ON auth_session(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires_at ON auth_session(expires_at);
DELETE FROM auth_session WHERE expires_at <= CURRENT_TIMESTAMP;
