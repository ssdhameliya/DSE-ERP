ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS billing_gstin TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS delivery_gstin TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS transporter_gstin TEXT;

UPDATE sales_header
SET billing_gstin = COALESCE(NULLIF(BTRIM(billing_gstin), ''), gstin, '')
WHERE billing_gstin IS NULL OR BTRIM(billing_gstin) = '';

UPDATE sales_header
SET delivery_gstin = COALESCE(NULLIF(BTRIM(delivery_gstin), ''), billing_gstin, gstin, '')
WHERE delivery_gstin IS NULL OR BTRIM(delivery_gstin) = '';
