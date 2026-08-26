-- v9.0.16: make Quotation Source resilient to historical Master category codes/names.

-- If an older QUOTATION SOURCE category already exists under a spaced/non-canonical code,
-- promote that row to the canonical code instead of creating a duplicate category.
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
WHERE NOT EXISTS (
    SELECT 1 FROM master_category c
    WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_code,'')),'_',''),' ',''))='QUOTATIONSOURCE'
       OR UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_name,'')),'_',''),' ',''))='QUOTATIONSOURCE'
);

-- Normalize historical lookup_type spellings to the selected Quotation Source category name.
UPDATE lookup_master l
SET lookup_type=(
    SELECT c.category_name FROM master_category c
    WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_code,'')),'_',''),' ',''))='QUOTATIONSOURCE'
       OR UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_name,'')),'_',''),' ',''))='QUOTATIONSOURCE'
    ORDER BY CASE WHEN UPPER(TRIM(COALESCE(c.category_code,'')))='QUOTATION_SOURCE' THEN 0 ELSE 1 END, c.id
    LIMIT 1
)
WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(l.lookup_type,'')),'_',''),' ',''))='QUOTATIONSOURCE';

-- Seed sensible defaults only when the Quotation Source Master contains no values at all.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT c.category_name,v.code,v.value,'Quotation source',v.ord,1
FROM (
    SELECT c.* FROM master_category c
    WHERE UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_code,'')),'_',''),' ',''))='QUOTATIONSOURCE'
       OR UPPER(REPLACE(REPLACE(TRIM(COALESCE(c.category_name,'')),'_',''),' ',''))='QUOTATIONSOURCE'
    ORDER BY CASE WHEN UPPER(TRIM(COALESCE(c.category_code,'')))='QUOTATION_SOURCE' THEN 0 ELSE 1 END, c.id
    LIMIT 1
) c
CROSS JOIN (VALUES
 ('QSRC001','Direct',10),
 ('QSRC002','Email',20),
 ('QSRC003','WhatsApp',30),
 ('QSRC004','Website',40),
 ('QSRC005','Referral',50),
 ('QSRC006','Other',60)
) AS v(code,value,ord)
WHERE NOT EXISTS (
    SELECT 1 FROM lookup_master x
    WHERE COALESCE(x.is_active,1)<>0
      AND UPPER(REPLACE(REPLACE(TRIM(COALESCE(x.lookup_type,'')),'_',''),' ',''))='QUOTATIONSOURCE'
);
