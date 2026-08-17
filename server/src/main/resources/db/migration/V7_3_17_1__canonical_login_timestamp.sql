-- v7.3.17 canonical UTC login timestamp.
-- Keep legacy users.last_login intact for rollback/compatibility; new releases write UTC text here.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_utc TEXT;
