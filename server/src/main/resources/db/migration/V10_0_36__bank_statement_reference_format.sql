-- DSE ERP 10.0.36: Bank Statement Reference Format Authority in Master Data
-- Ensure reference format code for Bank Statement is registered in lookup_master.

INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT ref.category_name, 'REF_BANK_STATEMENT', 'BST/XXXXX', 'Bank Statement transaction reference', 94, 1
FROM master_category ref
WHERE ref.category_code = 'REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type)) = UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code)) = 'REF_BANK_STATEMENT'
  );
