-- DSE ERP 9.0.63 stability release
-- 1) Restore Item-related Master category registrations that fresh databases could miss.
-- 2) Preserve Item category/HSN/unit snapshots on transaction lines for historical display/reporting.

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES
 ('CATEGORY','CATEGORY','Item categories used by Item Master and transaction item selection',10,1),
 ('UNIT','UNIT','Units of measure used by Item Master and transaction lines',20,1),
 ('MATERIAL','MATERIAL','Item materials used by Item Master',30,1),
 ('BRAND','BRAND','Item brands used by Item Master',40,1),
 ('GST','GST','GST percentages used by Item Master and transaction lines',50,1)
ON CONFLICT DO NOTHING;

-- Ensure lookup rows that were seeded before their category registration remain attached
-- to the canonical category name. User-created values are preserved; nothing is deleted.
UPDATE lookup_master lm
SET lookup_type = mc.category_name
FROM master_category mc
WHERE mc.category_code IN ('CATEGORY','UNIT','MATERIAL','BRAND','GST')
  AND UPPER(TRIM(lm.lookup_type)) = mc.category_code
  AND lm.lookup_type <> mc.category_name;

ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS category_snapshot TEXT;
ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS category_snapshot TEXT;
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS category_snapshot TEXT;
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS hsn_snapshot TEXT;
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS unit_snapshot TEXT;

UPDATE sales_line l
SET category_snapshot = i.category
FROM item_master i
WHERE i.item_code=l.item_code AND COALESCE(TRIM(l.category_snapshot),'')='';

UPDATE purchase_line l
SET category_snapshot = i.category
FROM item_master i
WHERE i.item_code=l.item_code AND COALESCE(TRIM(l.category_snapshot),'')='';

UPDATE quotation_line l
SET category_snapshot = i.category,
    hsn_snapshot = COALESCE(NULLIF(TRIM(l.hsn_snapshot),''), i.hsn),
    unit_snapshot = COALESCE(NULLIF(TRIM(l.unit_snapshot),''), i.unit)
FROM item_master i
WHERE i.item_code=l.item_code
  AND (COALESCE(TRIM(l.category_snapshot),'')=''
       OR COALESCE(TRIM(l.hsn_snapshot),'')=''
       OR COALESCE(TRIM(l.unit_snapshot),'')='');
