-- DSE ERP 10.0.35: Bank Entry & Expense Reference Format Authority in Master Data
-- Ensure reference format codes for Bank Entry, Bank Expense and Finance Voucher are registered in lookup_master.

INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT ref.category_name, v.lookup_code, v.lookup_value, v.description, v.display_order, 1
FROM master_category ref
CROSS JOIN (VALUES
    ('REF_FINANCE_VOUCHER', 'VCH-YYYY-XXXXX', 'Finance voucher reference', 90),
    ('REF_BANK_ENTRY', 'BE/DD-MM-YYYY/XXXX', 'Bank Entry voucher reference', 92),
    ('REF_BANK_EXPENSE', 'EXP-YYYY-XXXXX', 'Bank Expense voucher reference', 93)
) AS v(lookup_code, lookup_value, description, display_order)
WHERE ref.category_code = 'REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master x
      WHERE UPPER(TRIM(x.lookup_type)) = UPPER(TRIM(ref.category_name))
        AND UPPER(TRIM(x.lookup_code)) = UPPER(TRIM(v.lookup_code))
  );
