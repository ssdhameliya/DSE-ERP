-- v7.30.31 release schema guard.
-- Reinforces the runtime tables/columns used by Quotation and Return Refund APIs.
-- Every statement is idempotent so upgraded and partially-upgraded databases converge safely.

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
ALTER TABLE return_refund ADD COLUMN IF NOT EXISTS attachment_path TEXT;
ALTER TABLE return_refund ADD COLUMN IF NOT EXISTS bank_statement_transaction_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_return_refund_return_no ON return_refund(return_no);
CREATE INDEX IF NOT EXISTS idx_return_refund_bank_tx ON return_refund(bank_statement_transaction_id);

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS attachment_path TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS follow_up_date TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS salesperson TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS source TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS converted_invoice_no TEXT;
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;
