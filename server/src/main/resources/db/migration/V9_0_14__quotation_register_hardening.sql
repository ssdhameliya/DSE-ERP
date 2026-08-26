-- v9.0.14: make Quotation Source a normal Master Data category.
INSERT INTO master_category(category_code,category_name,description,display_order,is_active)
VALUES('QUOTATION_SOURCE','QUOTATION SOURCE','Source values used by Quotation create/edit and register filters',165,1)
ON CONFLICT DO NOTHING;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT mc.category_name,v.code,v.value,'Quotation source',v.ord,1
FROM master_category mc
CROSS JOIN (VALUES
 ('QSRC001','Direct',10),
 ('QSRC002','Email',20),
 ('QSRC003','WhatsApp',30),
 ('QSRC004','Website',40),
 ('QSRC005','Referral',50),
 ('QSRC006','Other',60)
) AS v(code,value,ord)
WHERE mc.category_code='QUOTATION_SOURCE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(mc.category_name))
        AND (UPPER(TRIM(x.lookup_code))=UPPER(v.code) OR UPPER(TRIM(x.lookup_value))=UPPER(v.value))
  );


-- Preserve the item description that belonged to the quotation line at save time.
ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS item_description_snapshot TEXT;
UPDATE quotation_line l
SET item_description_snapshot = COALESCE(NULLIF(TRIM(l.item_description_snapshot),''), i.description, l.item_code)
FROM item_master i
WHERE i.item_code=l.item_code
  AND NULLIF(TRIM(COALESCE(l.item_description_snapshot,'')),'') IS NULL;
UPDATE quotation_line
SET item_description_snapshot=item_code
WHERE NULLIF(TRIM(COALESCE(item_description_snapshot,'')),'') IS NULL;
