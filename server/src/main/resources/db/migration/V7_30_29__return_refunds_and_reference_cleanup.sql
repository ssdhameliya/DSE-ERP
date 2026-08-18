-- v7.30.29: auditable Return Refund ledger plus final legacy reference-format cleanup.
CREATE TABLE IF NOT EXISTS return_refund (
    id SERIAL PRIMARY KEY,
    return_no TEXT NOT NULL,
    refund_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    payment_mode TEXT NOT NULL,
    reference_no TEXT,
    bank_account TEXT,
    refunded_party TEXT,
    notes TEXT,
    attachment_path TEXT,
    refund_type TEXT NOT NULL DEFAULT 'PARTIAL',
    created_by TEXT,
    created_at TEXT,
    bank_statement_transaction_id BIGINT
);
CREATE INDEX IF NOT EXISTS idx_return_refund_return_no ON return_refund(return_no);
CREATE INDEX IF NOT EXISTS idx_return_refund_bank_tx ON return_refund(bank_statement_transaction_id);

-- Preserve already-recorded legacy refund totals exactly once.
INSERT INTO return_refund(return_no,refund_date,amount,payment_mode,reference_no,refunded_party,notes,refund_type,created_by,created_at)
SELECT r.return_no,
       COALESCE(MAX(CASE WHEN COALESCE(r.return_date,'') ~ '^\d{4}-\d{2}-\d{2}$' THEN CAST(r.return_date AS DATE) ELSE NULL END), CURRENT_DATE),
       SUM(COALESCE(r.refund_amount,0)),
       'Legacy Refund', '', MAX(COALESCE(pm.name,'')),
       'Migrated from return_register refund_amount during v7.30.29 upgrade',
       'LEGACY', 'Migration', COALESCE(MAX(r.updated_at),MAX(r.created_at),CURRENT_TIMESTAMP::text)
FROM return_register r
LEFT JOIN party_master pm ON pm.id=r.party_id
WHERE COALESCE(r.refund_amount,0) > 0
  AND NOT EXISTS (SELECT 1 FROM return_refund rr WHERE rr.return_no=r.return_no)
GROUP BY r.return_no;

-- REFERENCE FORMAT is the only active numbering authority.
DELETE FROM lookup_master
WHERE UPPER(TRIM(lookup_type)) IN (
    SELECT UPPER(TRIM(category_name)) FROM master_category
    WHERE category_code IN ('SALES_INVOICE_FORMAT','PURCHASE_INVOICE_FORMAT')
);
DELETE FROM master_category WHERE category_code IN ('SALES_INVOICE_FORMAT','PURCHASE_INVOICE_FORMAT');
