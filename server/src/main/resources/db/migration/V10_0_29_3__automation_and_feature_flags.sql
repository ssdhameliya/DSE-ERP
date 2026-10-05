-- V10_0_29_3__automation_and_feature_flags.sql
-- Smart 3-Way Match, Stock Replenishment, and Automation Rule Engine

CREATE TABLE IF NOT EXISTS automation_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    rule_name VARCHAR(160) NOT NULL,
    category VARCHAR(40) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_payload TEXT,
    last_executed_at TIMESTAMP,
    execution_count INT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(160),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS three_way_match_log (
    id BIGSERIAL PRIMARY KEY,
    po_id BIGINT,
    grn_id BIGINT,
    bill_id BIGINT,
    po_number VARCHAR(80),
    bill_number VARCHAR(80),
    supplier_name VARCHAR(180),
    po_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    grn_received_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    bill_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    variance_amount NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    variance_percentage NUMERIC(6, 2) NOT NULL DEFAULT 0.00,
    match_status VARCHAR(30) NOT NULL DEFAULT 'MATCHED',
    debit_note_id BIGINT,
    resolution_notes TEXT,
    matched_by VARCHAR(160),
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_3way_bill ON three_way_match_log(bill_number);
CREATE INDEX IF NOT EXISTS idx_3way_status ON three_way_match_log(match_status);

CREATE TABLE IF NOT EXISTS reorder_suggestion (
    id BIGSERIAL PRIMARY KEY,
    item_id INTEGER NOT NULL REFERENCES item_master(id),
    current_stock NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    reorder_level NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    suggested_qty NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    primary_supplier_id INTEGER REFERENCES party_master(id),
    estimated_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    draft_po_id BIGINT,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reorder_item ON reorder_suggestion(item_id);
CREATE INDEX IF NOT EXISTS idx_reorder_status ON reorder_suggestion(status);

-- Seed Default Automation Rules
INSERT INTO automation_rule (rule_code, rule_name, category, is_enabled, config_payload)
VALUES
    ('3WAY_PURCHASE_MATCH', 'Smart 3-Way Purchase Matching', 'PURCHASE', TRUE, '{"tolerance_pct": 0.5, "auto_debit_note": true}'),
    ('DYNAMIC_STOCK_REORDER', 'Dynamic Safety Buffer & Stock Reorder', 'INVENTORY', TRUE, '{"lead_time_days": 7, "safety_buffer_pct": 20}'),
    ('FIFO_PAYMENT_ALLOCATION', 'Smart FIFO & Exact Match Allocation', 'FINANCE', TRUE, '{"mode": "SUBSET_SUM_AND_FIFO"}'),
    ('EXECUTIVE_DAILY_BRIEFING', 'Executive 8:00 PM Daily Briefing', 'NOTIFICATION', FALSE, '{"time": "20:00", "whatsapp": true, "email": true}'),
    ('CREDIT_HARD_STOP', 'Customer Overdue Credit Hard Stop', 'SALES', FALSE, '{"max_overdue_days": 30}')
ON CONFLICT (rule_code) DO NOTHING;
