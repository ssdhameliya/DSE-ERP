-- DSE ERP 9.0.49 - Gate 2 / Gate 3 multi-user, integrity and recovery hardening

-- Shared mutable financial/reconciliation records now participate in optimistic versioning.
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bank_statement_transaction ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bank_statement_import ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

-- Fast authoritative payment aggregation/locking path.
CREATE INDEX IF NOT EXISTS idx_payment_record_document ON payment_record(document_type, document_id, id);
CREATE INDEX IF NOT EXISTS idx_bank_statement_tx_import_status ON bank_statement_transaction(import_id, status, id);

-- New/changed rows must respect these invariants. NOT VALID avoids blocking upgrade on legacy
-- rows while PostgreSQL still enforces the rule for new writes.
ALTER TABLE payment_record DROP CONSTRAINT IF EXISTS ck_payment_record_positive_amount;
ALTER TABLE payment_record ADD CONSTRAINT ck_payment_record_positive_amount CHECK (amount > 0) NOT VALID;

ALTER TABLE bank_statement_transaction DROP CONSTRAINT IF EXISTS ck_bank_statement_nonnegative_debit;
ALTER TABLE bank_statement_transaction ADD CONSTRAINT ck_bank_statement_nonnegative_debit CHECK (COALESCE(debit_amount,0) >= 0) NOT VALID;
ALTER TABLE bank_statement_transaction DROP CONSTRAINT IF EXISTS ck_bank_statement_nonnegative_credit;
ALTER TABLE bank_statement_transaction ADD CONSTRAINT ck_bank_statement_nonnegative_credit CHECK (COALESCE(credit_amount,0) >= 0) NOT VALID;
ALTER TABLE bank_statement_transaction DROP CONSTRAINT IF EXISTS ck_bank_statement_direction;
ALTER TABLE bank_statement_transaction ADD CONSTRAINT ck_bank_statement_direction CHECK (NOT (COALESCE(debit_amount,0) > 0 AND COALESCE(credit_amount,0) > 0)) NOT VALID;
