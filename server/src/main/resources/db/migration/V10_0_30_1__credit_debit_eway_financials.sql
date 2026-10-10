-- DSE ERP 10.0.30: Credit Notes, Debit Notes, E-Way Bill and Security Alignment

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS eway_bill_no VARCHAR(60);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS eway_bill_date VARCHAR(30);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS distance_km INTEGER DEFAULT 0;

CREATE TABLE IF NOT EXISTS credit_note_header (
    id BIGSERIAL PRIMARY KEY,
    credit_note_no VARCHAR(60) NOT NULL UNIQUE,
    note_date DATE NOT NULL DEFAULT CURRENT_DATE,
    original_invoice_no VARCHAR(60),
    party_id BIGINT NOT NULL,
    party_name VARCHAR(255) NOT NULL,
    party_gstin VARCHAR(20),
    reason_code VARCHAR(50) NOT NULL DEFAULT 'SALES_RETURN',
    taxable_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    roundoff_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    stock_returned BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'POSTED',
    gl_entry_id BIGINT,
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS credit_note_line (
    id BIGSERIAL PRIMARY KEY,
    credit_note_id BIGINT NOT NULL REFERENCES credit_note_header(id) ON DELETE CASCADE,
    item_id BIGINT,
    item_code VARCHAR(60),
    item_description VARCHAR(255) NOT NULL,
    hsn_sac VARCHAR(20),
    quantity NUMERIC(15,4) NOT NULL DEFAULT 1.0000,
    unit_price NUMERIC(15,4) NOT NULL DEFAULT 0.0000,
    gst_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    taxable_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_line_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS debit_note_header (
    id BIGSERIAL PRIMARY KEY,
    debit_note_no VARCHAR(60) NOT NULL UNIQUE,
    note_date DATE NOT NULL DEFAULT CURRENT_DATE,
    original_bill_no VARCHAR(60),
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    supplier_gstin VARCHAR(20),
    reason_code VARCHAR(50) NOT NULL DEFAULT 'PURCHASE_RETURN',
    taxable_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    roundoff_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    stock_returned BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'POSTED',
    gl_entry_id BIGINT,
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS debit_note_line (
    id BIGSERIAL PRIMARY KEY,
    debit_note_id BIGINT NOT NULL REFERENCES debit_note_header(id) ON DELETE CASCADE,
    item_id BIGINT,
    item_code VARCHAR(60),
    item_description VARCHAR(255) NOT NULL,
    hsn_sac VARCHAR(20),
    quantity NUMERIC(15,4) NOT NULL DEFAULT 1.0000,
    unit_cost NUMERIC(15,4) NOT NULL DEFAULT 0.0000,
    gst_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    taxable_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    sgst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    igst_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_line_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_credit_note_party ON credit_note_header(party_id);
CREATE INDEX IF NOT EXISTS idx_credit_note_date ON credit_note_header(note_date);
CREATE INDEX IF NOT EXISTS idx_debit_note_supplier ON debit_note_header(supplier_id);
CREATE INDEX IF NOT EXISTS idx_debit_note_date ON debit_note_header(note_date);

-- Security and Permissions Seed
INSERT INTO permissions (permission_key, module_name, action_name, description, active)
VALUES
    ('CREDIT_NOTE.VIEW', 'CREDIT_NOTE', 'VIEW', 'View Sales Credit Notes', 1),
    ('CREDIT_NOTE.CREATE', 'CREDIT_NOTE', 'CREATE', 'Create and issue Sales Credit Notes', 1),
    ('CREDIT_NOTE.EDIT', 'CREDIT_NOTE', 'EDIT', 'Edit Sales Credit Notes', 1),
    ('CREDIT_NOTE.PRINT', 'CREDIT_NOTE', 'PRINT', 'Print Sales Credit Notes', 1),
    ('DEBIT_NOTE.VIEW', 'DEBIT_NOTE', 'VIEW', 'View Purchase Debit Notes', 1),
    ('DEBIT_NOTE.CREATE', 'DEBIT_NOTE', 'CREATE', 'Create and issue Purchase Debit Notes', 1),
    ('DEBIT_NOTE.EDIT', 'DEBIT_NOTE', 'EDIT', 'Edit Purchase Debit Notes', 1),
    ('DEBIT_NOTE.PRINT', 'DEBIT_NOTE', 'PRINT', 'Print Purchase Debit Notes', 1),
    ('FINANCIAL_STATEMENTS.VIEW', 'FINANCIAL_STATEMENTS', 'VIEW', 'View Profit & Loss and Balance Sheet', 1),
    ('FINANCIAL_STATEMENTS.EXPORT', 'FINANCIAL_STATEMENTS', 'EXPORT', 'Export Financial Statements', 1),
    ('AGING_ANALYSIS.VIEW', 'AGING_ANALYSIS', 'VIEW', 'View Accounts Receivable / Payable Aging', 1),
    ('AGING_ANALYSIS.REMIND', 'AGING_ANALYSIS', 'REMIND', 'Send Customer Overdue Reminders', 1),
    ('AGING_ANALYSIS.EXPORT', 'AGING_ANALYSIS', 'EXPORT', 'Export Aging Reports', 1),
    ('EWAY_BILL.VIEW', 'EWAY_BILL', 'VIEW', 'View E-Way Bill and Transport Register', 1),
    ('EWAY_BILL.GENERATE', 'EWAY_BILL', 'GENERATE', 'Generate NIC E-Way Bill JSON', 1),
    ('EWAY_BILL.UPDATE_VEHICLE', 'EWAY_BILL', 'UPDATE_VEHICLE', 'Update Transporter and Vehicle Details', 1),
    ('BARCODE.VIEW', 'BARCODE', 'VIEW', 'View Barcode Studio', 1),
    ('BARCODE.PRINT', 'BARCODE', 'PRINT', 'Generate and Print Barcode Labels', 1)
ON CONFLICT (permission_key) DO UPDATE SET description = EXCLUDED.description, active = 1;

-- Grant all to ADMIN role
INSERT INTO role_permission (role_code, permission_id, allowed)
SELECT 'ADMIN', p.id, 1
FROM permissions p
WHERE p.module_name IN ('CREDIT_NOTE', 'DEBIT_NOTE', 'FINANCIAL_STATEMENTS', 'AGING_ANALYSIS', 'EWAY_BILL', 'BARCODE')
ON CONFLICT (UPPER(TRIM(role_code)), permission_id) WHERE TRIM(COALESCE(role_code, '')) <> ''
DO UPDATE SET allowed = 1;

-- Grant operational permissions to MANAGER role
INSERT INTO role_permission (role_code, permission_id, allowed)
SELECT 'MANAGER', p.id, 1
FROM permissions p
WHERE p.permission_key IN (
    'CREDIT_NOTE.VIEW', 'CREDIT_NOTE.CREATE', 'CREDIT_NOTE.EDIT', 'CREDIT_NOTE.PRINT',
    'DEBIT_NOTE.VIEW', 'DEBIT_NOTE.CREATE', 'DEBIT_NOTE.EDIT', 'DEBIT_NOTE.PRINT',
    'FINANCIAL_STATEMENTS.VIEW', 'FINANCIAL_STATEMENTS.EXPORT',
    'AGING_ANALYSIS.VIEW', 'AGING_ANALYSIS.REMIND', 'AGING_ANALYSIS.EXPORT',
    'EWAY_BILL.VIEW', 'EWAY_BILL.GENERATE', 'EWAY_BILL.UPDATE_VEHICLE',
    'BARCODE.VIEW', 'BARCODE.PRINT'
)
ON CONFLICT (UPPER(TRIM(role_code)), permission_id) WHERE TRIM(COALESCE(role_code, '')) <> ''
DO UPDATE SET allowed = 1;
