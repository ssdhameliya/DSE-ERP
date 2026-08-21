-- DSE ERP 8.2.4
-- Repairs upgraded workspaces where reminder_register predates the status column.

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS status TEXT;

UPDATE reminder_register
SET status = UPPER(BTRIM(status))
WHERE status IS NOT NULL;

UPDATE reminder_register
SET status = 'OPEN'
WHERE status IS NULL
   OR BTRIM(status) = ''
   OR status NOT IN ('OPEN', 'SNOOZED', 'COMPLETED', 'CANCELLED');

ALTER TABLE reminder_register ALTER COLUMN status SET DEFAULT 'OPEN';
ALTER TABLE reminder_register ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reminder_status_due_824
    ON reminder_register(status, due_date);
