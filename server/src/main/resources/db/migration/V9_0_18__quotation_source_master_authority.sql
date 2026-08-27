-- v9.0.18: final Quotation Source Master authority repair.
-- Runtime code now reads QUOTATION_SOURCE only through MasterDataService.valuesByCategoryCode().
-- This migration repairs databases where later imports reintroduced category-code lookup_type values
-- after the v9.0.15/v9.0.16 one-time migrations had already completed.

-- Ensure the canonical category code exists. Reuse a historical quotation-source category when possible.
UPDATE master_category c
SET category_code='QUOTATION_SOURCE'
WHERE c.id=(
    SELECT x.id FROM master_category x
    WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(x.category_code,'')),'_',''),' ',''))='QUOTATIONSOURCE'
       OR UPPER(REPLACE(REPLACE(TRIM(COALESCE(x.category_name,'')),'_',''),' ',''))='QUOTATIONSOURCE'
    ORDER BY CASE WHEN UPPER(TRIM(COALESCE(x.category_code,'')))='QUOTATION_SOURCE' THEN 0 ELSE 1 END, x.id
    LIMIT 1
)
AND NOT EXISTS (SELECT 1 FROM master_category z WHERE UPPER(TRIM(z.category_code))='QUOTATION_SOURCE');

INSERT INTO master_category(category_code,category_name,description,display_order,is_active)
SELECT 'QUOTATION_SOURCE','QUOTATION SOURCE','Source values used by Quotation create/edit and register filters',165,1
WHERE NOT EXISTS (SELECT 1 FROM master_category c WHERE UPPER(TRIM(COALESCE(c.category_code,'')))='QUOTATION_SOURCE');

-- Remove duplicate historical rows before normalization so the existing case-insensitive
-- unique indexes on (lookup_type, lookup_code) and (lookup_type, lookup_value) cannot be violated.
-- The lowest id is retained; only rows already identified as Quotation Source variants participate.
DELETE FROM lookup_master duplicate
USING lookup_master keeper
WHERE duplicate.id > keeper.id
  AND UPPER(REPLACE(REPLACE(TRIM(COALESCE(duplicate.lookup_type,'')),'_',''),' ',''))='QUOTATIONSOURCE'
  AND UPPER(REPLACE(REPLACE(TRIM(COALESCE(keeper.lookup_type,'')),'_',''),' ',''))='QUOTATIONSOURCE'
  AND (
      (NULLIF(TRIM(COALESCE(duplicate.lookup_code,'')),'') IS NOT NULL
       AND UPPER(TRIM(duplicate.lookup_code))=UPPER(TRIM(keeper.lookup_code)))
      OR
      (NULLIF(TRIM(COALESCE(duplicate.lookup_value,'')),'') IS NOT NULL
       AND UPPER(TRIM(duplicate.lookup_value))=UPPER(TRIM(keeper.lookup_value)))
  );

-- Every Quotation Source lookup must use the canonical category NAME because MasterDataService
-- resolves category code -> category name before loading values.
UPDATE lookup_master l
SET lookup_type=(SELECT c.category_name FROM master_category c WHERE UPPER(TRIM(c.category_code))='QUOTATION_SOURCE' ORDER BY c.id LIMIT 1)
WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(l.lookup_type,'')),'_',''),' ',''))='QUOTATIONSOURCE'
  AND EXISTS (SELECT 1 FROM master_category c WHERE UPPER(TRIM(c.category_code))='QUOTATION_SOURCE');

-- Seed defaults only if the canonical category has no values at all.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT c.category_name,v.code,v.value,'Quotation source',v.ord,1
FROM master_category c
CROSS JOIN (VALUES
 ('QSRC001','Direct',10),
 ('QSRC002','Email',20),
 ('QSRC003','WhatsApp',30),
 ('QSRC004','Website',40),
 ('QSRC005','Referral',50),
 ('QSRC006','Other',60)
) AS v(code,value,ord)
WHERE UPPER(TRIM(c.category_code))='QUOTATION_SOURCE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(c.category_name))
  );
