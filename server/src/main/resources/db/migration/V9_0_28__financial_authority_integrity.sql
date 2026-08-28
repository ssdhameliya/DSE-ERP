-- DSE ERP 9.0.28: financial authority and rejection audit integrity.
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS rejected_by TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS rejected_at TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS rejected_by TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS rejected_at TEXT;

-- Older releases stored the rejecting user/time in approval columns. Preserve that audit trail
-- in dedicated rejection fields, then restore approval fields to their true meaning.
UPDATE sales_header
SET rejected_by = COALESCE(NULLIF(rejected_by,''), approved_by),
    rejected_at = COALESCE(NULLIF(rejected_at,''), approved_at),
    approved_by = NULL,
    approved_at = NULL
WHERE UPPER(COALESCE(document_status,'')) = 'REJECTED';

UPDATE purchase_header
SET rejected_by = COALESCE(NULLIF(rejected_by,''), approved_by),
    rejected_at = COALESCE(NULLIF(rejected_at,''), approved_at),
    approved_by = NULL,
    approved_at = NULL
WHERE UPPER(COALESCE(document_status,'')) = 'REJECTED';
