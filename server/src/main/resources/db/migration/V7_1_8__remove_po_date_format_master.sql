-- 7.1.8: PO Date is an optional, user-entered Sales field.
-- Remove the obsolete PO DATE FORMATE master category and its seeded lookup.
-- The always-run base schema no longer seeds this category. This one-time
-- migration removes it from databases created by earlier 7.1.x builds.

DELETE FROM lookup_master
WHERE UPPER(TRIM(COALESCE(lookup_code, ''))) = 'POFMT001'
   OR UPPER(TRIM(COALESCE(lookup_type, ''))) IN ('PO_DATE_FORMAT', 'PO DATE FORMATE');

DELETE FROM master_category
WHERE UPPER(TRIM(COALESCE(category_code, ''))) = 'PO_DATE_FORMAT'
   OR UPPER(TRIM(COALESCE(category_name, ''))) = 'PO DATE FORMATE';
