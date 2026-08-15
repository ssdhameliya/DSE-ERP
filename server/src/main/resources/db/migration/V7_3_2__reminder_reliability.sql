-- DSE ERP 7.3.2
-- Reminder reliability normalization for upgraded workspaces.
-- Deliberately uses plain SQL only because SecurityFinancialMigrationRunner
-- splits and executes one statement at a time.

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS reference_type TEXT;
ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS party_id INTEGER;
ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS snoozed_until TEXT;
ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS completed_at TEXT;
ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS updated_at TEXT;

UPDATE reminder_register
SET status = UPPER(BTRIM(status))
WHERE status IS NOT NULL;

UPDATE reminder_register
SET status = 'OPEN'
WHERE status IS NULL
   OR BTRIM(status) = ''
   OR status NOT IN ('OPEN', 'SNOOZED', 'COMPLETED');

UPDATE reminder_register
SET priority = UPPER(BTRIM(priority))
WHERE priority IS NOT NULL;

UPDATE reminder_register
SET priority = 'NORMAL'
WHERE priority IS NULL
   OR BTRIM(priority) = ''
   OR priority NOT IN ('LOW', 'NORMAL', 'HIGH', 'URGENT');

UPDATE reminder_register
SET created_by = 'System'
WHERE created_by IS NULL OR BTRIM(created_by) = '';

CREATE INDEX IF NOT EXISTS idx_reminder_status_due_732
    ON reminder_register(status, due_date);
