-- DSE ERP v9.0.24
-- Canonical Sale/Purchase + Return lifecycle.
-- Existing pre-v9.0.24 active Returns already moved stock at creation time, so they are
-- promoted to APPROVED during migration. New Returns move stock only on Admin approval.

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS approval_requested_by TEXT;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS approval_requested_at TEXT;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS approved_by TEXT;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS approved_at TEXT;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE return_register ADD COLUMN IF NOT EXISTS settlement_due_date DATE;

-- Preserve historical stock/accounting meaning: these legacy active states had already
-- posted the return stock movement before the approval workflow existed.
UPDATE return_register
SET status = 'APPROVED',
    approved_by = COALESCE(NULLIF(approved_by,''),'System Migration'),
    approved_at = COALESCE(NULLIF(approved_at,''), NULLIF(updated_at,''), NULLIF(created_at,''))
WHERE UPPER(COALESCE(status,'')) IN ('PENDING','PARTIAL','COMPLETED');

UPDATE return_register
SET refund_status = CASE
    WHEN UPPER(COALESCE(refund_status,'')) IN ('REFUNDED','PAID','COMPLETED') THEN 'PAID'
    WHEN UPPER(COALESCE(refund_status,''))='PARTIAL' THEN 'PARTIAL'
    ELSE 'PENDING'
END;

-- Document Status now means document lifecycle only. Payment/return settlement is shown
-- independently. Historical operational/payment-derived values become APPROVED when the
-- underlying approval record says the source document was approved.
UPDATE sales_header
SET document_status='APPROVED'
WHERE UPPER(COALESCE(approval_status,'APPROVED'))='APPROVED'
  AND UPPER(COALESCE(document_status,'')) IN ('PENDING','IN PROGRESS','COMPLETED','RETURNED','PARTIALLY RETURNED','DRAFT','');

UPDATE purchase_header
SET document_status='APPROVED'
WHERE UPPER(COALESCE(approval_status,'APPROVED'))='APPROVED'
  AND UPPER(COALESCE(document_status,'')) IN ('PENDING','IN PROGRESS','COMPLETED','RETURNED','PARTIALLY RETURNED','DRAFT','');

CREATE INDEX IF NOT EXISTS idx_return_register_lifecycle_invoice
    ON return_register(return_type, invoice_no, status);
CREATE INDEX IF NOT EXISTS idx_return_register_settlement_due
    ON return_register(settlement_due_date)
    WHERE settlement_due_date IS NOT NULL;
