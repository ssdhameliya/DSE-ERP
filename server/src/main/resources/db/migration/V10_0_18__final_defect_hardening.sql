-- DSE ERP 10.0.18 final defect hardening.
-- Keep this migration declarative so the release-owned migration runner can split it safely.

ALTER TABLE report_schedule ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
UPDATE return_register SET return_type='SALES RETURN' WHERE UPPER(TRIM(COALESCE(return_type,'')))='SALE RETURN';

ALTER TABLE registration_request ADD COLUMN IF NOT EXISTS mfa_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE registration_request ADD COLUMN IF NOT EXISTS mfa_last_attempt_at TIMESTAMP;
ALTER TABLE registration_request ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
UPDATE registration_request
SET expires_at = requested_at + INTERVAL '30 minutes'
WHERE expires_at IS NULL AND status='MFA_ENROLLMENT_PENDING';

ALTER TABLE backup_history ALTER COLUMN file_size TYPE BIGINT USING file_size::BIGINT;

CREATE TABLE IF NOT EXISTS auth_challenge (
    challenge_id VARCHAR(128) PRIMARY KEY,
    purpose VARCHAR(40) NOT NULL,
    challenge_key VARCHAR(320) NOT NULL,
    binding_hash VARCHAR(128) NOT NULL,
    user_id INTEGER,
    recipient TEXT,
    salt_base64 TEXT NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_auth_challenge_lookup ON auth_challenge(purpose,challenge_key,issued_at DESC);
CREATE INDEX IF NOT EXISTS ix_auth_challenge_expiry ON auth_challenge(expires_at);

CREATE TABLE IF NOT EXISTS auth_totp_login_challenge (
    challenge_id VARCHAR(128) PRIMARY KEY,
    user_id INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_auth_totp_login_expiry ON auth_totp_login_challenge(expires_at);

CREATE TABLE IF NOT EXISTS login_throttle (
    throttle_key VARCHAR(400) PRIMARY KEY,
    window_started TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_login_throttle_blocked ON login_throttle(blocked_until);

CREATE TABLE IF NOT EXISTS backup_scheduler_state (
    state_key VARCHAR(120) PRIMARY KEY,
    state_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE report_schedule_run
SET status='FAILED', finished_at=COALESCE(NULLIF(TRIM(finished_at),''),CURRENT_TIMESTAMP::text),
    error_message=COALESCE(NULLIF(TRIM(error_message),''),'Superseded duplicate active run during 10.0.18 migration')
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY schedule_id ORDER BY started_at DESC,id DESC) AS rn
        FROM report_schedule_run WHERE status='RUNNING'
    ) ranked WHERE rn>1
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_report_schedule_run_one_running
    ON report_schedule_run(schedule_id)
    WHERE status='RUNNING';


CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_ci
    ON users ((LOWER(TRIM(username))));
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_ci
    ON users ((LOWER(TRIM(email))))
    WHERE NULLIF(TRIM(COALESCE(email,'')),'') IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_open_username_ci
    ON registration_request ((LOWER(TRIM(username))))
    WHERE status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL');
CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_open_email_ci
    ON registration_request ((LOWER(TRIM(email))))
    WHERE status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL');
CREATE INDEX IF NOT EXISTS ix_registration_mfa_expiry
    ON registration_request(expires_at)
    WHERE status='MFA_ENROLLMENT_PENDING';

-- Backfill immutable line snapshots once so read paths never perform an Item Master query per invoice line.
UPDATE sales_line l SET
    item_description_snapshot = COALESCE(NULLIF(TRIM(l.item_description_snapshot),''), i.description),
    category_snapshot = COALESCE(NULLIF(TRIM(l.category_snapshot),''), i.category),
    hsn_snapshot = COALESCE(NULLIF(TRIM(l.hsn_snapshot),''), i.hsn),
    unit_snapshot = COALESCE(NULLIF(TRIM(l.unit_snapshot),''), i.unit),
    item_remarks_snapshot = COALESCE(NULLIF(TRIM(l.item_remarks_snapshot),''), i.remarks)
FROM item_master i
WHERE i.item_code=l.item_code
  AND (NULLIF(TRIM(l.item_description_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.category_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.hsn_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.unit_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.item_remarks_snapshot),'') IS NULL);

UPDATE purchase_line l SET
    item_description_snapshot = COALESCE(NULLIF(TRIM(l.item_description_snapshot),''), i.description),
    category_snapshot = COALESCE(NULLIF(TRIM(l.category_snapshot),''), i.category),
    hsn_snapshot = COALESCE(NULLIF(TRIM(l.hsn_snapshot),''), i.hsn),
    unit_snapshot = COALESCE(NULLIF(TRIM(l.unit_snapshot),''), i.unit),
    item_remarks_snapshot = COALESCE(NULLIF(TRIM(l.item_remarks_snapshot),''), i.remarks)
FROM item_master i
WHERE i.item_code=l.item_code
  AND (NULLIF(TRIM(l.item_description_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.category_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.hsn_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.unit_snapshot),'') IS NULL
    OR NULLIF(TRIM(l.item_remarks_snapshot),'') IS NULL);
