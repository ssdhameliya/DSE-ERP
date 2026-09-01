-- Same-version 9.0.56 repair: guarantees Customer 360 persistence exists even when
-- an earlier corrective migration key was recorded against an incomplete database.
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
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS designation VARCHAR(160);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS department VARCHAR(160);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS mobile VARCHAR(80);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS email VARCHAR(240);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS created_by VARCHAR(160);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS created_at TEXT;
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS updated_by VARCHAR(160);
ALTER TABLE party_contact ADD COLUMN IF NOT EXISTS updated_at TEXT;
ALTER TABLE party_contact ALTER COLUMN created_at TYPE TEXT USING created_at::text;
ALTER TABLE party_contact ALTER COLUMN updated_at TYPE TEXT USING updated_at::text;
UPDATE party_contact SET created_at=COALESCE(NULLIF(created_at,''),CURRENT_TIMESTAMP::text),updated_at=COALESCE(NULLIF(updated_at,''),CURRENT_TIMESTAMP::text);
ALTER TABLE party_contact ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE party_contact ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE party_contact ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE party_contact ALTER COLUMN updated_at SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_party_contact_party ON party_contact(party_id,is_primary DESC,contact_name);
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
ALTER TABLE party_note ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE party_note ADD COLUMN IF NOT EXISTS created_by VARCHAR(160);
ALTER TABLE party_note ADD COLUMN IF NOT EXISTS created_at TEXT;
ALTER TABLE party_note ADD COLUMN IF NOT EXISTS updated_by VARCHAR(160);
ALTER TABLE party_note ADD COLUMN IF NOT EXISTS updated_at TEXT;
ALTER TABLE party_note ALTER COLUMN created_at TYPE TEXT USING created_at::text;
ALTER TABLE party_note ALTER COLUMN updated_at TYPE TEXT USING updated_at::text;
UPDATE party_note SET created_at=COALESCE(NULLIF(created_at,''),CURRENT_TIMESTAMP::text),updated_at=COALESCE(NULLIF(updated_at,''),CURRENT_TIMESTAMP::text);
ALTER TABLE party_note ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE party_note ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE party_note ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE party_note ALTER COLUMN updated_at SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_party_note_party ON party_note(party_id,id DESC);

CREATE TABLE IF NOT EXISTS document_attachment (
    id BIGSERIAL PRIMARY KEY,
    document_type TEXT NOT NULL,
    document_id INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    storage_ref TEXT NOT NULL,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_document_attachment_document ON document_attachment(document_type,document_id,id);
