ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS auth_signing_key (
    key_id SMALLINT PRIMARY KEY,
    secret_base64 VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_signing_key_singleton CHECK (key_id = 1)
);

CREATE TABLE IF NOT EXISTS auth_token_revocation (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_token_revocation_expiry
    ON auth_token_revocation(expires_at);

DELETE FROM auth_token_revocation WHERE expires_at <= CURRENT_TIMESTAMP;
