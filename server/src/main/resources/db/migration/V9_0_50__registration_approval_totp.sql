-- DSE ERP 9.0.50: non-Admin registration approval + authenticator TOTP
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret_enc TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED';
UPDATE users SET approval_status='APPROVED' WHERE approval_status IS NULL OR TRIM(approval_status)='';

CREATE TABLE IF NOT EXISTS registration_request (
 id BIGSERIAL PRIMARY KEY,
 username VARCHAR(160) NOT NULL,
 password_hash TEXT NOT NULL,
 full_name VARCHAR(240) NOT NULL,
 email VARCHAR(320) NOT NULL,
 requested_role VARCHAR(80) NOT NULL,
 totp_secret_enc TEXT NOT NULL,
 email_verified INTEGER NOT NULL DEFAULT 1,
 mfa_verified INTEGER NOT NULL DEFAULT 0,
 status VARCHAR(40) NOT NULL DEFAULT 'MFA_ENROLLMENT_PENDING',
 requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by INTEGER,
 reviewed_at TIMESTAMP,
 rejection_reason TEXT,
 row_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_request_username_open ON registration_request(LOWER(username)) WHERE status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL');
CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_request_email_open ON registration_request(LOWER(email)) WHERE status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL');
CREATE INDEX IF NOT EXISTS ix_registration_request_status ON registration_request(status, requested_at);
