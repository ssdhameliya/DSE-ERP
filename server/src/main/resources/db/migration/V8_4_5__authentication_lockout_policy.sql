-- DSE ERP v8.4.5 authentication lockout policy.
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(32) NOT NULL DEFAULT 'NONE';
UPDATE users SET failed_attempts=COALESCE(failed_attempts,0), mfa_failed_attempts=COALESCE(mfa_failed_attempts,0);
UPDATE users SET lock_reason='ADMIN' WHERE COALESCE(locked,0)=1 AND COALESCE(NULLIF(TRIM(lock_reason),''),'NONE')='NONE';
UPDATE users SET lock_reason='NONE' WHERE COALESCE(locked,0)=0;
