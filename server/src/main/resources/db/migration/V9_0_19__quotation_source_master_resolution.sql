-- v9.0.19: final compatibility repair for Quotation Source Master resolution.
-- Handles exact/case-normalized QUOTATION_SOURCE plus legacy generic SOURCE masters.

-- Canonicalize a historical quotation-source category code when there is no exact canonical row.
UPDATE master_category c
SET category_code='QUOTATION_SOURCE'
WHERE c.id=(
    SELECT x.id FROM master_category x
    WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.category_code,'')),'_',''),' ',''),'-','')) IN ('QUOTATIONSOURCE','SOURCE')
       OR UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.category_name,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
    ORDER BY CASE
        WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.category_code,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE' THEN 0
        ELSE 1 END, x.id
    LIMIT 1
)
AND NOT EXISTS (SELECT 1 FROM master_category z WHERE z.category_code='QUOTATION_SOURCE');

INSERT INTO master_category(category_code,category_name,description,display_order,is_active)
SELECT 'QUOTATION_SOURCE','QUOTATION SOURCE','Source values used by Quotation create/edit and register filters',165,1
WHERE NOT EXISTS (SELECT 1 FROM master_category c WHERE c.category_code='QUOTATION_SOURCE');

UPDATE master_category SET is_active=1 WHERE category_code='QUOTATION_SOURCE' AND COALESCE(is_active,1)<>1;

-- Remove duplicate historical quotation-source rows before type normalization so the
-- case-insensitive unique lookup indexes cannot be violated by the UPDATE below.
DELETE FROM lookup_master duplicate
USING lookup_master keeper
WHERE duplicate.id > keeper.id
  AND UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(duplicate.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
  AND UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(keeper.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
  AND (UPPER(TRIM(duplicate.lookup_value))=UPPER(TRIM(keeper.lookup_value))
       OR UPPER(TRIM(duplicate.lookup_code))=UPPER(TRIM(keeper.lookup_code)));

-- Normalize historical QUOTATION SOURCE lookup types to the canonical category name.
UPDATE lookup_master l
SET lookup_type=(SELECT c.category_name FROM master_category c WHERE c.category_code='QUOTATION_SOURCE' LIMIT 1)
WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(l.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE';

-- If users maintained their values under a generic SOURCE Master, COPY the values into the
-- canonical Quotation Source category. SOURCE itself is left intact for backward compatibility.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT c.category_name,
       'QSRC_SRC_'||s.id::text,
       s.lookup_value,s.description,s.display_order,COALESCE(s.is_active,1)
FROM lookup_master s
CROSS JOIN (SELECT category_name FROM master_category WHERE category_code='QUOTATION_SOURCE' LIMIT 1) c
WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(s.lookup_type,'')),'_',''),' ',''),'-',''))='SOURCE'
  AND COALESCE(s.is_active,1)=1
  AND NULLIF(TRIM(COALESCE(s.lookup_value,'')),'') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(c.category_name))
        AND UPPER(TRIM(x.lookup_value))=UPPER(TRIM(s.lookup_value))
  );

-- Seed defaults only when neither canonical nor legacy SOURCE has any active values.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT c.category_name,v.code,v.value,'Quotation source',v.ord,1
FROM master_category c
CROSS JOIN (VALUES
 ('QSRC001','Direct',10),('QSRC002','Email',20),('QSRC003','WhatsApp',30),
 ('QSRC004','Website',40),('QSRC005','Referral',50),('QSRC006','Other',60)
) AS v(code,value,ord)
WHERE c.category_code='QUOTATION_SOURCE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE COALESCE(x.is_active,1)=1
        AND UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.lookup_type,'')),'_',''),' ',''),'-','')) IN ('QUOTATIONSOURCE','SOURCE')
  );
