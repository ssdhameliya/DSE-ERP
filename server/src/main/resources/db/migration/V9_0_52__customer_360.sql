-- DSE ERP 9.0.52 Customer 360°
-- Adds only customer-owned relationship data. Existing quotation/order/project/sale/payment
-- records remain authoritative and are never duplicated here.
ALTER TABLE party_master ADD COLUMN IF NOT EXISTS attachment_path TEXT;

CREATE TABLE IF NOT EXISTS party_contact (
    id BIGSERIAL PRIMARY KEY,
    party_id INTEGER NOT NULL REFERENCES party_master(id) ON DELETE CASCADE,
    contact_name VARCHAR(180) NOT NULL,
    designation VARCHAR(160),
    department VARCHAR(160),
    mobile VARCHAR(80),
    email VARCHAR(240),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(160),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_party_contact_party ON party_contact(party_id, is_primary DESC, contact_name);
CREATE UNIQUE INDEX IF NOT EXISTS uq_party_contact_one_primary ON party_contact(party_id) WHERE is_primary=TRUE;

CREATE TABLE IF NOT EXISTS party_note (
    id BIGSERIAL PRIMARY KEY,
    party_id INTEGER NOT NULL REFERENCES party_master(id) ON DELETE CASCADE,
    note_text TEXT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(160),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(160),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_party_note_party ON party_note(party_id, id DESC);
