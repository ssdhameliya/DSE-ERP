CREATE TABLE IF NOT EXISTS payment_record (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(20) NOT NULL,
    document_id INTEGER NOT NULL,
    payment_date VARCHAR(10) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    payment_mode VARCHAR(80) NOT NULL,
    reference_no TEXT,
    notes TEXT,
    created_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT (CURRENT_TIMESTAMP::text),
    received_from TEXT,
    payment_type VARCHAR(40) NOT NULL DEFAULT 'PARTIAL',
    attachment_path TEXT
);
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS received_from TEXT;
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS payment_type VARCHAR(40) NOT NULL DEFAULT 'PARTIAL';
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS attachment_path TEXT;

CREATE TABLE IF NOT EXISTS bank_statement_import (
    id BIGSERIAL PRIMARY KEY,
    bank_name VARCHAR(120) NOT NULL,
    bank_account VARCHAR(120) NOT NULL,
    account_holder VARCHAR(200),
    statement_from VARCHAR(10),
    statement_to VARCHAR(10),
    currency VARCHAR(10) DEFAULT 'INR',
    opening_balance NUMERIC(18,2),
    closing_balance NUMERIC(18,2),
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_debit NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_credit NUMERIC(18,2) NOT NULL DEFAULT 0,
    reconciled_count INTEGER NOT NULL DEFAULT 0,
    reconciliation_percent NUMERIC(7,3) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'IMPORTED',
    source_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    source_file_name TEXT,
    source_csv TEXT,
    imported_by VARCHAR(120),
    imported_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

CREATE TABLE IF NOT EXISTS bank_statement_transaction (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES bank_statement_import(id) ON DELETE CASCADE,
    source_row_number INTEGER,
    transaction_timestamp VARCHAR(40),
    transaction_date VARCHAR(10) NOT NULL,
    value_date VARCHAR(10),
    original_description TEXT,
    original_reference TEXT,
    debit_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    balance NUMERIC(18,2),
    status VARCHAR(40) NOT NULL DEFAULT 'UNMATCHED',
    suggested_match_type VARCHAR(20),
    suggested_match_id INTEGER,
    suggested_confidence NUMERIC(7,2),
    notes TEXT,
    transaction_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text,
    updated_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_tx_import ON bank_statement_transaction(import_id, transaction_date, id);
CREATE INDEX IF NOT EXISTS idx_bank_stmt_tx_status ON bank_statement_transaction(status);

CREATE TABLE IF NOT EXISTS bank_reconciliation_allocation (
    id BIGSERIAL PRIMARY KEY,
    statement_transaction_id BIGINT NOT NULL REFERENCES bank_statement_transaction(id),
    target_type VARCHAR(20) NOT NULL,
    target_id INTEGER NOT NULL,
    allocated_amount NUMERIC(18,2) NOT NULL,
    payment_record_id INTEGER,
    finance_entry_id INTEGER,
    created_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text,
    reversed_at VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS bank_reconciliation_audit (
    id BIGSERIAL PRIMARY KEY,
    statement_transaction_id BIGINT NOT NULL REFERENCES bank_statement_transaction(id),
    event_type VARCHAR(60) NOT NULL,
    event_detail TEXT,
    previous_status VARCHAR(40),
    new_status VARCHAR(40),
    performed_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);
