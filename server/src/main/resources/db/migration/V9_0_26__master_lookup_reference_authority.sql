-- DSE ERP v9.0.26
-- Stable category-code authority for Master lookup reference numbering.
-- Existing GENxxx lookup identifiers are historical and intentionally remain unchanged.

-- Preserve any user-customized numbering format that was previously stored under the
-- mutable category display name (for example REF_LOOKUP_BANK ACCOUNT). The new key
-- uses the immutable category_code (REF_LOOKUP_BANK_ACCOUNT).
INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT ref.category_name,
       'REF_LOOKUP_' || UPPER(TRIM(c.category_code)),
       legacy.lookup_value,
       COALESCE(legacy.description, c.category_name || ' Master code reference'),
       COALESCE(legacy.display_order, 0),
       COALESCE(legacy.is_active, 1)
FROM master_category ref
JOIN master_category c ON UPPER(TRIM(c.category_code)) <> 'REFERENCE_FORMAT'
JOIN lookup_master legacy
  ON UPPER(TRIM(legacy.lookup_type)) = UPPER(TRIM(ref.category_name))
 AND UPPER(TRIM(legacy.lookup_code)) = UPPER('REF_LOOKUP_' || TRIM(c.category_name))
WHERE UPPER(TRIM(ref.category_code))='REFERENCE_FORMAT'
  AND UPPER(TRIM(legacy.lookup_code)) <> UPPER('REF_LOOKUP_' || TRIM(c.category_code))
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master stable
      WHERE UPPER(TRIM(stable.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(stable.lookup_code))=UPPER('REF_LOOKUP_' || TRIM(c.category_code))
  )
ON CONFLICT DO NOTHING;

-- The old display-name aliases are no longer authoritative. Keep their rows for audit/history
-- but retire them from active Reference Format choices after the stable copy exists.
UPDATE lookup_master legacy
SET is_active=0
FROM master_category ref, master_category c
WHERE UPPER(TRIM(ref.category_code))='REFERENCE_FORMAT'
  AND UPPER(TRIM(legacy.lookup_type))=UPPER(TRIM(ref.category_name))
  AND UPPER(TRIM(legacy.lookup_code))=UPPER('REF_LOOKUP_' || TRIM(c.category_name))
  AND UPPER(TRIM(legacy.lookup_code))<>UPPER('REF_LOOKUP_' || TRIM(c.category_code))
  AND EXISTS (
      SELECT 1 FROM lookup_master stable
      WHERE UPPER(TRIM(stable.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(stable.lookup_code))=UPPER('REF_LOOKUP_' || TRIM(c.category_code))
  );

-- Seed standard category-specific formats only when there is no stable custom format.
INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT ref.category_name, v.lookup_code, v.lookup_value, v.description, v.display_order, 1
FROM master_category ref
CROSS JOIN (VALUES
    ('REF_LOOKUP_CATEGORY','CATXXX','Category Master code reference',210),
    ('REF_LOOKUP_UNIT','UNTXXX','Unit Master code reference',220),
    ('REF_LOOKUP_MATERIAL','MATXXX','Material Master code reference',230),
    ('REF_LOOKUP_BRAND','BRDXXX','Brand Master code reference',240),
    ('REF_LOOKUP_GST','GSTXXX','GST Master code reference',250),
    ('REF_LOOKUP_ROLE','ROLXXX','Role Master code reference',260),
    ('REF_LOOKUP_DISCOUNT','DSCXXX','Discount Master code reference',270),
    ('REF_LOOKUP_GST_TYPE','GTPXXX','GST Type Master code reference',280),
    ('REF_LOOKUP_TRANSPORTER','TRNXXX','Transporter Master code reference',290),
    ('REF_LOOKUP_PAYMENT_TERMS','PTMXXX','Payment Terms Master code reference',300),
    ('REF_LOOKUP_CHARGES','CHGXXX','Charges Master code reference',310),
    ('REF_LOOKUP_PAYMENT_MODE','PMDXXX','Payment Mode Master code reference',320),
    ('REF_LOOKUP_EXPENSE_CATEGORY','EXPXXX','Expense Category Master code reference',330),
    ('REF_LOOKUP_BANK_ACCOUNT','BNKXXX','Bank Account Master code reference',340),
    ('REF_LOOKUP_QUOTATION_SOURCE','QTSXXX','Quotation Source Master code reference',350),
    ('REF_LOOKUP_REFERENCE_FORMAT','RFMXXX','Reference Format Master code reference',360)
) AS v(lookup_code,lookup_value,description,display_order)
WHERE UPPER(TRIM(ref.category_code))='REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type))=UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code))=UPPER(TRIM(v.lookup_code))
  )
ON CONFLICT DO NOTHING;
