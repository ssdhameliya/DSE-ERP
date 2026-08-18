-- v7.30.30 corrective repair: guarantee Quotation register columns required by the API.
-- Safe and idempotent for existing installations.
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS attachment_path TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS follow_up_date TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS salesperson TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS source TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS converted_invoice_no TEXT;
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;
