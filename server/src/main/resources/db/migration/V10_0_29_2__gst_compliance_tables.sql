-- V10_0_29_2__gst_compliance_tables.sql
-- GSTR-2B Auto-Reconciliation, GSTR-1 Schema Engine, and TDS/TCS Tracking

CREATE TABLE IF NOT EXISTS gstr2b_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    return_period VARCHAR(10) NOT NULL,
    supplier_gstin VARCHAR(20) NOT NULL,
    supplier_trade_name VARCHAR(180),
    invoice_number VARCHAR(80) NOT NULL,
    normalized_invoice_no VARCHAR(80) NOT NULL,
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'B2B',
    taxable_value NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    igst_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    cgst_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    sgst_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    cess_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    itc_eligibility VARCHAR(10) NOT NULL DEFAULT 'Y',
    match_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    erp_purchase_id BIGINT,
    variance_amount NUMERIC(15, 4) DEFAULT 0.0000,
    action_taken VARCHAR(40),
    reconciled_by VARCHAR(160),
    reconciled_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gstr2b_period ON gstr2b_reconciliation(return_period);
CREATE INDEX IF NOT EXISTS idx_gstr2b_gstin ON gstr2b_reconciliation(supplier_gstin);
CREATE INDEX IF NOT EXISTS idx_gstr2b_norm_inv ON gstr2b_reconciliation(normalized_invoice_no);
CREATE INDEX IF NOT EXISTS idx_gstr2b_match ON gstr2b_reconciliation(match_status);

CREATE TABLE IF NOT EXISTS gstr1_export_cache (
    id BIGSERIAL PRIMARY KEY,
    return_period VARCHAR(10) NOT NULL,
    table_name VARCHAR(20) NOT NULL,
    payload_json TEXT NOT NULL,
    record_count INT NOT NULL DEFAULT 0,
    total_taxable NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    total_tax NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    checksum_hash VARCHAR(80),
    generated_by VARCHAR(160),
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gstr1_cache_period ON gstr1_export_cache(return_period, table_name);

CREATE TABLE IF NOT EXISTS tds_tcs_entry (
    id BIGSERIAL PRIMARY KEY,
    pan_number VARCHAR(15) NOT NULL,
    party_id INTEGER REFERENCES party_master(id),
    section_code VARCHAR(20) NOT NULL,
    financial_year VARCHAR(15) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    document_ref VARCHAR(100),
    taxable_base NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    cumulative_threshold_base NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0000,
    tax_deducted NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    challan_reference VARCHAR(100),
    is_deposited BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tds_pan_fy ON tds_tcs_entry(pan_number, financial_year);
CREATE INDEX IF NOT EXISTS idx_tds_section ON tds_tcs_entry(section_code);
