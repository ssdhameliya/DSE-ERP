-- v9.0.22: make Quotation Source a normal Master-backed field, identical to other Master dropdowns.
-- Runtime code no longer contains quotation-specific SOURCE/LEAD SOURCE/etc fallback logic.
-- This one-time compatibility migration moves historical Source values into the canonical
-- QUOTATION_SOURCE category so all future reads use the generic MasterDataService path.

-- 1) Promote an existing quotation-source category to the exact canonical code when possible.
UPDATE master_category c
SET category_code='QUOTATION_SOURCE'
WHERE c.id=(
    SELECT x.id
    FROM master_category x
    WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.category_code,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
       OR UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(x.category_name,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
    ORDER BY CASE WHEN UPPER(TRIM(COALESCE(x.category_code,'')))='QUOTATION_SOURCE' THEN 0 ELSE 1 END, x.id
    LIMIT 1
)
AND NOT EXISTS (SELECT 1 FROM master_category z WHERE z.category_code='QUOTATION_SOURCE');

-- 2) Ensure the same canonical category contract used by other Master-backed controls.
INSERT INTO master_category(category_code,category_name,description,display_order,is_active)
SELECT 'QUOTATION_SOURCE','QUOTATION SOURCE','Source values used by Quotation create/edit and register filters',165,1
WHERE NOT EXISTS (SELECT 1 FROM master_category c WHERE c.category_code='QUOTATION_SOURCE');

UPDATE master_category
SET is_active=1,
    description='Source values used by Quotation create/edit and register filters'
WHERE category_code='QUOTATION_SOURCE';

-- 3) Normalize historical QUOTATION SOURCE lookup_type spellings onto the canonical category name.
-- Remove duplicate historical rows first to preserve the case-insensitive unique indexes.
DELETE FROM lookup_master duplicate
USING lookup_master keeper
WHERE duplicate.id > keeper.id
  AND UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(duplicate.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
  AND UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(keeper.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE'
  AND (
      (NULLIF(TRIM(COALESCE(duplicate.lookup_value,'')),'') IS NOT NULL
       AND UPPER(TRIM(duplicate.lookup_value))=UPPER(TRIM(keeper.lookup_value)))
      OR
      (NULLIF(TRIM(COALESCE(duplicate.lookup_code,'')),'') IS NOT NULL
       AND UPPER(TRIM(duplicate.lookup_code))=UPPER(TRIM(keeper.lookup_code)))
  );

UPDATE lookup_master l
SET lookup_type=(SELECT c.category_name FROM master_category c WHERE c.category_code='QUOTATION_SOURCE' LIMIT 1)
WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(l.lookup_type,'')),'_',''),' ',''),'-',''))='QUOTATIONSOURCE';

-- 4) One-time compatibility bridge: copy user-maintained values from historical Source masters
-- into QUOTATION SOURCE. The original Master categories remain untouched for any other feature.
-- From this release onward Quotation itself reads only QUOTATION_SOURCE through the generic Master API.
WITH canonical AS (
    SELECT category_name FROM master_category WHERE category_code='QUOTATION_SOURCE' LIMIT 1
), source_types AS (
    SELECT DISTINCT category_name AS lookup_type
    FROM master_category
    WHERE UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(category_code,'')),'_',''),' ',''),'-','')) IN
          ('SOURCE','LEADSOURCE','SALESSOURCE','CUSTOMERSOURCE','ENQUIRYSOURCE','INQUIRYSOURCE')
       OR UPPER(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(category_name,'')),'_',''),' ',''),'-','')) IN
          ('SOURCE','LEADSOURCE','SALESSOURCE','CUSTOMERSOURCE','ENQUIRYSOURCE','INQUIRYSOURCE')
    UNION SELECT 'SOURCE'
    UNION SELECT 'LEAD SOURCE'
    UNION SELECT 'SALES SOURCE'
    UNION SELECT 'CUSTOMER SOURCE'
    UNION SELECT 'ENQUIRY SOURCE'
    UNION SELECT 'INQUIRY SOURCE'
)
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT c.category_name,
       'QSRC_MIG_'||s.id::text,
       s.lookup_value,
       s.description,
       s.display_order,
       COALESCE(s.is_active,1)
FROM lookup_master s
CROSS JOIN canonical c
WHERE EXISTS (
    SELECT 1 FROM source_types st
    WHERE UPPER(TRIM(st.lookup_type))=UPPER(TRIM(s.lookup_type))
)
  AND NULLIF(TRIM(COALESCE(s.lookup_value,'')),'') IS NOT NULL
  AND UPPER(TRIM(s.lookup_type))<>UPPER(TRIM(c.category_name))
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(c.category_name))
        AND UPPER(TRIM(x.lookup_value))=UPPER(TRIM(s.lookup_value))
  );
