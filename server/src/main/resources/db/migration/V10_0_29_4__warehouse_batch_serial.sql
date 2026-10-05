-- V10_0_29_4__warehouse_batch_serial.sql
-- Multi-Warehouse, Batch/Expiry (FEFO) & Serial Registry

CREATE TABLE IF NOT EXISTS warehouse_master (
    id BIGSERIAL PRIMARY KEY,
    warehouse_code VARCHAR(30) NOT NULL UNIQUE,
    warehouse_name VARCHAR(160) NOT NULL,
    location VARCHAR(200),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS item_batch_registry (
    id BIGSERIAL PRIMARY KEY,
    item_id INTEGER NOT NULL REFERENCES item_master(id),
    warehouse_id BIGINT REFERENCES warehouse_master(id),
    batch_number VARCHAR(80) NOT NULL,
    manufacturing_date DATE,
    expiry_date DATE,
    quantity NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    unit_cost NUMERIC(15, 4) NOT NULL DEFAULT 0.0000,
    is_quarantine BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_batch_item ON item_batch_registry(item_id, batch_number);
CREATE INDEX IF NOT EXISTS idx_batch_expiry ON item_batch_registry(expiry_date);

CREATE TABLE IF NOT EXISTS item_serial_registry (
    id BIGSERIAL PRIMARY KEY,
    item_id INTEGER NOT NULL REFERENCES item_master(id),
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    warehouse_id BIGINT REFERENCES warehouse_master(id),
    batch_id BIGINT REFERENCES item_batch_registry(id),
    status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    purchase_id BIGINT,
    sale_id BIGINT,
    warranty_expiry_date DATE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_serial_item ON item_serial_registry(item_id);
CREATE INDEX IF NOT EXISTS idx_serial_status ON item_serial_registry(status);

-- Seed Default Central Warehouse
INSERT INTO warehouse_master (warehouse_code, warehouse_name, location, is_default)
VALUES ('WH001', 'Central Warehouse', 'Primary Facility', TRUE)
ON CONFLICT (warehouse_code) DO NOTHING;
