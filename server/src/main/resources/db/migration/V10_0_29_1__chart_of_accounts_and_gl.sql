-- V10_0_29_1__chart_of_accounts_and_gl.sql
-- Indian 5-Tier Chart of Accounts and Double-Entry General Ledger

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id BIGSERIAL PRIMARY KEY,
    account_code VARCHAR(30) NOT NULL UNIQUE,
    account_name VARCHAR(160) NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    account_subtype VARCHAR(60),
    parent_id BIGINT REFERENCES chart_of_accounts(id),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    opening_balance NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    current_balance NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_coa_code ON chart_of_accounts(account_code);
CREATE INDEX IF NOT EXISTS idx_coa_type ON chart_of_accounts(account_type);

CREATE TABLE IF NOT EXISTS journal_entry (
    id BIGSERIAL PRIMARY KEY,
    entry_number VARCHAR(50) NOT NULL UNIQUE,
    entry_date DATE NOT NULL,
    entry_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40),
    reference_id BIGINT,
    reference_no VARCHAR(100),
    narration TEXT,
    total_debit NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_credit NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    posted_by VARCHAR(160),
    posted_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_je_number ON journal_entry(entry_number);
CREATE INDEX IF NOT EXISTS idx_je_date ON journal_entry(entry_date);
CREATE INDEX IF NOT EXISTS idx_je_ref ON journal_entry(reference_type, reference_id);

CREATE TABLE IF NOT EXISTS journal_line (
    id BIGSERIAL PRIMARY KEY,
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entry(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES chart_of_accounts(id),
    line_number INT NOT NULL,
    debit_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    credit_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    party_id INTEGER REFERENCES party_master(id),
    line_narration VARCHAR(255),
    cost_center VARCHAR(100),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jl_entry_id ON journal_line(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_jl_account_id ON journal_line(account_id);
CREATE INDEX IF NOT EXISTS idx_jl_party_id ON journal_line(party_id);

-- Idempotent Standard Indian Chart of Accounts Seed
INSERT INTO chart_of_accounts(account_code, account_name, account_type, account_subtype, is_system)
VALUES
    ('1010', 'Cash on Hand', 'ASSET', 'CURRENT_ASSET', TRUE),
    ('1020', 'Bank Current Account', 'ASSET', 'CURRENT_ASSET', TRUE),
    ('1040', 'Sundry Debtors (Trade Receivables)', 'ASSET', 'CURRENT_ASSET', TRUE),
    ('1060', 'Stock-in-Trade (Inventory Asset)', 'ASSET', 'CURRENT_ASSET', TRUE),
    ('1081', 'Input CGST Pool', 'ASSET', 'TAX_CREDIT', TRUE),
    ('1082', 'Input SGST Pool', 'ASSET', 'TAX_CREDIT', TRUE),
    ('1083', 'Input IGST Pool', 'ASSET', 'TAX_CREDIT', TRUE),
    ('1090', 'TDS Receivable (Sec 194Q/194C)', 'ASSET', 'CURRENT_ASSET', TRUE),
    ('2010', 'Sundry Creditors (Trade Payables)', 'LIABILITY', 'CURRENT_LIABILITY', TRUE),
    ('2050', 'Customer Advances', 'LIABILITY', 'CURRENT_LIABILITY', TRUE),
    ('2081', 'Output CGST Payable', 'LIABILITY', 'DUTIES_AND_TAXES', TRUE),
    ('2082', 'Output SGST Payable', 'LIABILITY', 'DUTIES_AND_TAXES', TRUE),
    ('2083', 'Output IGST Payable', 'LIABILITY', 'DUTIES_AND_TAXES', TRUE),
    ('2085', 'RCM Tax Liability', 'LIABILITY', 'DUTIES_AND_TAXES', TRUE),
    ('2090', 'TDS / TCS Payable', 'LIABILITY', 'DUTIES_AND_TAXES', TRUE),
    ('3010', 'Owner Capital Account', 'EQUITY', 'CAPITAL', TRUE),
    ('3030', 'Retained Earnings', 'EQUITY', 'RESERVES', TRUE),
    ('3999', 'Opening Balance Suspense', 'EQUITY', 'SUSPENSE', TRUE),
    ('4010', 'Domestic Sales Revenue', 'REVENUE', 'OPERATING_REVENUE', TRUE),
    ('4020', 'Inter-State Sales Revenue', 'REVENUE', 'OPERATING_REVENUE', TRUE),
    ('4030', 'Export Sales Revenue', 'REVENUE', 'OPERATING_REVENUE', TRUE),
    ('4090', 'Discounts Received', 'REVENUE', 'OTHER_INCOME', TRUE),
    ('4095', 'Freight & Handling Income', 'REVENUE', 'OTHER_INCOME', TRUE),
    ('5010', 'Cost of Goods Sold', 'EXPENSE', 'DIRECT_EXPENSE', TRUE),
    ('5020', 'Raw Material Purchases', 'EXPENSE', 'DIRECT_EXPENSE', TRUE),
    ('5030', 'Freight Inward (Cartage)', 'EXPENSE', 'DIRECT_EXPENSE', TRUE),
    ('5050', 'Discounts Allowed', 'EXPENSE', 'INDIRECT_EXPENSE', TRUE),
    ('5060', 'Bank Charges & Gateway MDR Fees', 'EXPENSE', 'INDIRECT_EXPENSE', TRUE),
    ('5070', 'Salaries & Staff Welfare', 'EXPENSE', 'INDIRECT_EXPENSE', TRUE),
    ('5080', 'Office Rent & Utilities', 'EXPENSE', 'INDIRECT_EXPENSE', TRUE)
ON CONFLICT (account_code) DO NOTHING;
