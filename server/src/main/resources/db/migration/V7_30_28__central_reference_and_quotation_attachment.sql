-- v7.30.28: one Master Data reference-number source plus server-owned Quotation attachments.
-- Existing issued document numbers are preserved; only future generated references use REFERENCE_FORMAT.

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS attachment_path TEXT;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active)
VALUES('REFERENCE_FORMAT','REFERENCE FORMAT','Auto-generated reference number patterns. Use YYYY / YY for year and XX... for sequence digits.',160,1)
ON CONFLICT (category_code) DO UPDATE
SET category_name=EXCLUDED.category_name,
    description=EXCLUDED.description,
    display_order=EXCLUDED.display_order;

-- Preserve the user's legacy Sale/Purchase pattern when the centralized value does not already exist.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT ref.category_name,
       'REF_SALES',
       COALESCE((
           SELECT lm.lookup_value
           FROM lookup_master lm
           JOIN master_category legacy ON legacy.category_name=lm.lookup_type
           WHERE legacy.category_code='SALES_INVOICE_FORMAT'
             AND COALESCE(lm.is_active,1)=1
             AND TRIM(COALESCE(lm.lookup_value,''))<>''
           ORDER BY COALESCE(lm.display_order,0), lm.id
           LIMIT 1
       ), 'IN/DD-MM-YYYY/XXXX'),
       'Sales invoice reference',10,1
FROM master_category ref
WHERE ref.category_code='REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code))='REF_SALES'
  )
ON CONFLICT DO NOTHING;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT ref.category_name,
       'REF_PURCHASE',
       COALESCE((
           SELECT lm.lookup_value
           FROM lookup_master lm
           JOIN master_category legacy ON legacy.category_name=lm.lookup_type
           WHERE legacy.category_code='PURCHASE_INVOICE_FORMAT'
             AND COALESCE(lm.is_active,1)=1
             AND TRIM(COALESCE(lm.lookup_value,''))<>''
           ORDER BY COALESCE(lm.display_order,0), lm.id
           LIMIT 1
       ), 'PUR/DD-MM-YYYY/XXXX'),
       'Purchase invoice reference',20,1
FROM master_category ref
WHERE ref.category_code='REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code))='REF_PURCHASE'
  )
ON CONFLICT DO NOTHING;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT ref.category_name,v.lookup_code,v.lookup_value,v.description,v.display_order,1
FROM master_category ref
CROSS JOIN (VALUES
    ('REF_QUOTATION','QT-YYYY-XXXX','Quotation reference',30),
    ('REF_SALES_RETURN','SAL-RET-YYYY-XXXX','Sales Return reference',40),
    ('REF_PURCHASE_RETURN','PUR-RET-YYYY-XXXX','Purchase Return reference',50),
    ('REF_ITEM','ITMXXX','Item code reference',60),
    ('REF_CUSTOMER','CUSXXX','Customer reference',70),
    ('REF_SUPPLIER','SUPXXX','Supplier reference',80)
) AS v(lookup_code,lookup_value,description,display_order)
WHERE ref.category_code='REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code))=UPPER(TRIM(v.lookup_code))
  )
ON CONFLICT DO NOTHING;

-- The centralized rows above are now authoritative. Remove the duplicate legacy UI categories.
DELETE FROM lookup_master
WHERE UPPER(TRIM(lookup_type)) IN (
    SELECT UPPER(TRIM(category_name)) FROM master_category
    WHERE category_code IN ('SALES_INVOICE_FORMAT','PURCHASE_INVOICE_FORMAT')
);

DELETE FROM master_category
WHERE category_code IN ('SALES_INVOICE_FORMAT','PURCHASE_INVOICE_FORMAT');
