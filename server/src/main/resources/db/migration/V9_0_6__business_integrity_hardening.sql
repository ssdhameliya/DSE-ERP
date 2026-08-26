-- DSE ERP 9.0.6 business-integrity hardening.
-- Keep this migration idempotent because the managed runtime applies it during upgrades.

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS source_line_id INTEGER;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_name_snapshot TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_email_snapshot TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_phone_snapshot TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_gstin_snapshot TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_address_snapshot TEXT;

ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS item_description_snapshot TEXT;
ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS hsn_snapshot TEXT;
ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS unit_snapshot TEXT;
ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS item_remarks_snapshot TEXT;
ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS unit_cost_snapshot NUMERIC(19,4) NOT NULL DEFAULT 0;

UPDATE sales_header h SET
 customer_name_snapshot=COALESCE(NULLIF(customer_name_snapshot,''),p.name),
 customer_email_snapshot=COALESCE(NULLIF(customer_email_snapshot,''),p.email),
 customer_phone_snapshot=COALESCE(NULLIF(customer_phone_snapshot,''),p.phone),
 customer_gstin_snapshot=COALESCE(NULLIF(customer_gstin_snapshot,''),p.gstin),
 customer_address_snapshot=COALESCE(NULLIF(customer_address_snapshot,''),p.address)
FROM party_master p WHERE p.id=h.customer_id;

UPDATE sales_line sl SET
 item_description_snapshot=COALESCE(NULLIF(item_description_snapshot,''),i.description),
 hsn_snapshot=COALESCE(NULLIF(hsn_snapshot,''),i.hsn),
 unit_snapshot=COALESCE(NULLIF(unit_snapshot,''),i.unit),
 item_remarks_snapshot=COALESCE(NULLIF(item_remarks_snapshot,''),i.remarks),
 unit_cost_snapshot=CASE WHEN COALESCE(unit_cost_snapshot,0)=0 THEN COALESCE(i.purchase_price,0) ELSE unit_cost_snapshot END
FROM item_master i WHERE i.item_code=sl.item_code;

-- Backfill an exact source line only where the original document has one unambiguous row for that item.
UPDATE return_register r SET source_line_id=(
 SELECT MIN(sl.id) FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id
 WHERE UPPER(r.return_type) IN ('SALE RETURN','SALES RETURN') AND sh.invoice_no=r.invoice_no AND sl.item_code=r.item_code
 HAVING COUNT(*)=1
) WHERE source_line_id IS NULL AND UPPER(return_type) IN ('SALE RETURN','SALES RETURN');
UPDATE return_register r SET source_line_id=(
 SELECT MIN(pl.id) FROM purchase_line pl JOIN purchase_header ph ON ph.id=pl.purchase_id
 WHERE UPPER(r.return_type)='PURCHASE RETURN' AND ph.invoice_no=r.invoice_no AND pl.item_code=r.item_code
 HAVING COUNT(*)=1
) WHERE source_line_id IS NULL AND UPPER(return_type)='PURCHASE RETURN';

ALTER TABLE finance_register ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric,2);

CREATE TABLE IF NOT EXISTS inventory_cost_state (
 item_code TEXT PRIMARY KEY REFERENCES item_master(item_code),
 quantity NUMERIC(19,4) NOT NULL DEFAULT 0,
 average_unit_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
 updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS inventory_cost_ledger (
 id BIGSERIAL PRIMARY KEY,
 item_code TEXT NOT NULL REFERENCES item_master(item_code),
 movement_type TEXT NOT NULL,
 reference_id INTEGER,
 quantity_change NUMERIC(19,4) NOT NULL,
 unit_cost NUMERIC(19,4) NOT NULL,
 value_change NUMERIC(19,2) NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_inventory_cost_ledger_item_created ON inventory_cost_ledger(item_code,id);
INSERT INTO inventory_cost_state(item_code,quantity,average_unit_cost,updated_at)
SELECT item_code,COALESCE(opening_stock,0),COALESCE(purchase_price,0),CURRENT_TIMESTAMP::text FROM item_master
ON CONFLICT(item_code) DO NOTHING;

-- Server-side validation is authoritative; values above were normalized to decimal storage.
