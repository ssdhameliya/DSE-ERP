-- Idempotent repair for databases first opened by 7.1.6, whose migration
-- resource shipped but was not registered by the startup migration runner.
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS same_as_billing BOOLEAN;

UPDATE sales_header
SET same_as_billing = CASE
    WHEN NULLIF(BTRIM(delivery_address), '') IS NULL THEN TRUE
    WHEN LOWER(BTRIM(COALESCE(delivery_address, ''))) = LOWER(BTRIM(COALESCE(billing_address, '')))
     AND LOWER(BTRIM(COALESCE(delivery_gstin, ''))) = LOWER(BTRIM(COALESCE(billing_gstin, gstin, ''))) THEN TRUE
    ELSE FALSE
END
WHERE same_as_billing IS NULL;

ALTER TABLE sales_header ALTER COLUMN same_as_billing SET DEFAULT TRUE;
ALTER TABLE sales_header ALTER COLUMN same_as_billing SET NOT NULL;

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS category VARCHAR(40) NOT NULL DEFAULT 'GENERAL';
CREATE INDEX IF NOT EXISTS idx_notifications_category_created ON notifications(category, created_at DESC);
