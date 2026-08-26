-- v9.0.9 corrective integrity hardening
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS supplier_name_snapshot TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS supplier_email_snapshot TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS supplier_phone_snapshot TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS supplier_gstin_snapshot TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS supplier_address_snapshot TEXT;

ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS item_description_snapshot TEXT;
ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS hsn_snapshot TEXT;
ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS unit_snapshot TEXT;
ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS item_remarks_snapshot TEXT;
ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS unit_cost_snapshot NUMERIC(18,4);

UPDATE purchase_header h SET
 supplier_name_snapshot=COALESCE(NULLIF(h.supplier_name_snapshot,''),p.name),
 supplier_email_snapshot=COALESCE(NULLIF(h.supplier_email_snapshot,''),p.email),
 supplier_phone_snapshot=COALESCE(NULLIF(h.supplier_phone_snapshot,''),p.phone),
 supplier_gstin_snapshot=COALESCE(NULLIF(h.supplier_gstin_snapshot,''),p.gstin),
 supplier_address_snapshot=COALESCE(NULLIF(h.supplier_address_snapshot,''),p.address)
FROM party_master p WHERE p.id=h.supplier_id;

UPDATE purchase_line l SET
 item_description_snapshot=COALESCE(NULLIF(l.item_description_snapshot,''),i.description),
 hsn_snapshot=COALESCE(NULLIF(l.hsn_snapshot,''),i.hsn),
 unit_snapshot=COALESCE(NULLIF(l.unit_snapshot,''),i.unit),
 item_remarks_snapshot=COALESCE(NULLIF(l.item_remarks_snapshot,''),i.remarks),
 unit_cost_snapshot=COALESCE(l.unit_cost_snapshot, CASE WHEN COALESCE(l.quantity,0)>0 THEN (COALESCE(l.rate,0)*(1-COALESCE(l.discount_percent,0)/100.0)) ELSE COALESCE(i.purchase_price,0) END)
FROM item_master i WHERE i.item_code=l.item_code;

-- Repair any historical orphan allocation reference before enforcing future integrity.
UPDATE bank_reconciliation_allocation a SET finance_entry_id=NULL
WHERE finance_entry_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finance_register f WHERE f.id=a.finance_entry_id);

DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_bank_reconciliation_allocation_finance_entry') THEN
  ALTER TABLE bank_reconciliation_allocation
   ADD CONSTRAINT fk_bank_reconciliation_allocation_finance_entry
   FOREIGN KEY(finance_entry_id) REFERENCES finance_register(id) ON DELETE RESTRICT;
 END IF;
END $$;

-- One safe parser for legacy textual dates. Invalid or impossible dates become NULL instead of aborting a report.
CREATE OR REPLACE FUNCTION dse_safe_date(value TEXT) RETURNS DATE
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
 v TEXT := BTRIM(COALESCE(value,''));
 parsed DATE;
BEGIN
 IF v ~ '^\d{4}-\d{2}-\d{2}' THEN
  parsed := TO_DATE(LEFT(v,10),'YYYY-MM-DD');
  IF TO_CHAR(parsed,'YYYY-MM-DD')=LEFT(v,10) THEN RETURN parsed; END IF;
 ELSIF v ~ '^\d{2}/\d{2}/\d{4}' THEN
  parsed := TO_DATE(LEFT(v,10),'DD/MM/YYYY');
  IF TO_CHAR(parsed,'DD/MM/YYYY')=LEFT(v,10) THEN RETURN parsed; END IF;
 ELSIF v ~ '^\d{2}-\d{2}-\d{4}' THEN
  parsed := TO_DATE(LEFT(v,10),'DD-MM-YYYY');
  IF TO_CHAR(parsed,'DD-MM-YYYY')=LEFT(v,10) THEN RETURN parsed; END IF;
 END IF;
 RETURN NULL;
EXCEPTION WHEN OTHERS THEN
 RETURN NULL;
END $$;
