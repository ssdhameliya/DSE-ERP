-- DSE ERP 10.0.20: explicit pending state for administrator-triggered authenticator enrollment/recovery.
CREATE TABLE IF NOT EXISTS auth_totp_enrollment_pending (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Existing TOTP secrets predate this marker and are treated as enrolled.
-- New/reset enrollments insert a marker until the first valid authenticator code is verified.
