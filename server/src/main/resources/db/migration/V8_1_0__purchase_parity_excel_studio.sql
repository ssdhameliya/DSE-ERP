-- v8.1.0 Purchase/Sales parity. Additive only; existing purchases remain valid.
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS billing_address TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS delivery_address TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS billing_gstin TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS delivery_gstin TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS gst_type TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS transporter_gstin TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS vehicle_number TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS contact_person TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS contact_person_mobile TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS order_no TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS po_date TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS same_as_billing BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE purchase_header
SET billing_address = COALESCE(NULLIF(billing_address,''),
    (SELECT address FROM party_master WHERE party_master.id=purchase_header.supplier_id)),
    delivery_address = COALESCE(NULLIF(delivery_address,''), billing_address,
    (SELECT address FROM party_master WHERE party_master.id=purchase_header.supplier_id)),
    billing_gstin = COALESCE(NULLIF(billing_gstin,''),
    (SELECT gstin FROM party_master WHERE party_master.id=purchase_header.supplier_id)),
    delivery_gstin = COALESCE(NULLIF(delivery_gstin,''), billing_gstin,
    (SELECT gstin FROM party_master WHERE party_master.id=purchase_header.supplier_id)),
    gst_type = COALESCE(NULLIF(gst_type,''), NULLIF(gst_treatment,''), 'GST'),
    notes = COALESCE(NULLIF(notes,''), remarks)
WHERE TRUE;

CREATE TABLE IF NOT EXISTS purchase_charge (
    id SERIAL PRIMARY KEY,
    purchase_id INTEGER NOT NULL REFERENCES purchase_header(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    charge_code TEXT NOT NULL,
    charge_name TEXT NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    taxable BOOLEAN NOT NULL DEFAULT FALSE,
    gst_percent NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (gst_percent >= 0 AND gst_percent <= 100),
    CONSTRAINT uq_purchase_charge_sequence UNIQUE (purchase_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS idx_purchase_charge_purchase ON purchase_charge(purchase_id, sequence_no);
