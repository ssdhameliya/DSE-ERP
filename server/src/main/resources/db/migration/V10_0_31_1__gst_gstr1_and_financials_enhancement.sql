-- DSE ERP 10.0.31: GST Compliance Enhancements & Financial Statements Overhaul

ALTER TABLE gstr2b_reconciliation ADD COLUMN IF NOT EXISTS supplier_notice_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE gstr2b_reconciliation ADD COLUMN IF NOT EXISTS supplier_notice_text TEXT;
ALTER TABLE gstr2b_reconciliation ADD COLUMN IF NOT EXISTS supplier_notice_sent_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS gstr3b_return_history (
    id BIGSERIAL PRIMARY KEY,
    return_period VARCHAR(10) NOT NULL UNIQUE,
    outward_taxable NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    outward_igst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    outward_cgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    outward_sgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    rcm_taxable NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    rcm_tax NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_igst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_cgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_sgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_reversed_igst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_reversed_cgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    itc_reversed_sgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    net_cash_igst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    net_cash_cgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    net_cash_sgst NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    total_cash_payable NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPUTED',
    filed_date DATE,
    arn_number VARCHAR(60),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gstr3b_period ON gstr3b_return_history(return_period);

CREATE TABLE IF NOT EXISTS financial_ratio_snapshot (
    id BIGSERIAL PRIMARY KEY,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    gross_margin_pct NUMERIC(8, 2) NOT NULL DEFAULT 0.00,
    net_margin_pct NUMERIC(8, 2) NOT NULL DEFAULT 0.00,
    current_ratio NUMERIC(8, 2) NOT NULL DEFAULT 0.00,
    quick_ratio NUMERIC(8, 2) NOT NULL DEFAULT 0.00,
    dso_days NUMERIC(8, 1) NOT NULL DEFAULT 0.0,
    dpo_days NUMERIC(8, 1) NOT NULL DEFAULT 0.0,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fin_ratio_period ON financial_ratio_snapshot(period_start, period_end);
