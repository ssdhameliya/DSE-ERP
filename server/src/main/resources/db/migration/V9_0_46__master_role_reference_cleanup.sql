-- DSE ERP v9.0.46
-- Clean Master reference authority and configurable public-registration role.
--
-- 1) Historical GENxxx lookup codes are replaced with the category-specific reference
--    sequence introduced in v9.0.26. Database ids, lookup values, status, display order
--    and all business-facing text remain unchanged.
-- 2) The previous GENxxx code is retained as a compatibility alias so old imports,
--    support searches and external references can still resolve the same Master row.
-- 3) Public registration keeps SALES as the upgrade-safe default, but the selected
--    active non-Admin Role Master identity is now stored in application_setting.

CREATE TABLE IF NOT EXISTS lookup_code_alias (
    id BIGSERIAL PRIMARY KEY,
    lookup_id BIGINT NOT NULL REFERENCES lookup_master(id) ON DELETE CASCADE,
    lookup_type TEXT NOT NULL,
    alias_code TEXT NOT NULL,
    canonical_code TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lookup_code_alias_type_code
    ON lookup_code_alias (UPPER(TRIM(lookup_type)), UPPER(TRIM(alias_code)));
CREATE INDEX IF NOT EXISTS idx_lookup_code_alias_lookup_id
    ON lookup_code_alias (lookup_id);

-- Capture aliases before rewriting the historical technical code.
WITH legacy AS (
    SELECT lm.id,
           lm.lookup_type,
           UPPER(TRIM(lm.lookup_code)) AS old_code,
           CASE UPPER(TRIM(mc.category_code))
             WHEN 'CATEGORY' THEN 'CAT'
             WHEN 'UNIT' THEN 'UNT'
             WHEN 'UOM' THEN 'UNT'
             WHEN 'MATERIAL' THEN 'MAT'
             WHEN 'BRAND' THEN 'BRD'
             WHEN 'GST' THEN 'GST'
             WHEN 'ROLE' THEN 'ROL'
             WHEN 'DISCOUNT' THEN 'DSC'
             WHEN 'GST_TYPE' THEN 'GTP'
             WHEN 'TRANSPORTER' THEN 'TRN'
             WHEN 'PAYMENT_TERMS' THEN 'PTM'
             WHEN 'CHARGES' THEN 'CHG'
             WHEN 'PAYMENT_MODE' THEN 'PMD'
             WHEN 'EXPENSE_CATEGORY' THEN 'EXP'
             WHEN 'BANK_ACCOUNT' THEN 'BNK'
             WHEN 'QUOTATION_SOURCE' THEN 'QTS'
             WHEN 'REFERENCE_FORMAT' THEN 'RFM'
             ELSE SUBSTRING(REGEXP_REPLACE(UPPER(TRIM(mc.category_code)), '[^A-Z0-9]', '', 'g') || 'MST' FROM 1 FOR 3)
           END AS prefix
      FROM lookup_master lm
      JOIN master_category mc ON UPPER(TRIM(mc.category_name)) = UPPER(TRIM(lm.lookup_type))
     WHERE UPPER(TRIM(lm.lookup_code)) ~ '^GEN[0-9]+$'
), numbered AS (
    SELECT l.*,
           ROW_NUMBER() OVER (PARTITION BY UPPER(TRIM(l.lookup_type)), l.prefix ORDER BY l.id) AS rn,
           COALESCE((
               SELECT MAX(CAST(SUBSTRING(UPPER(TRIM(x.lookup_code)) FROM LENGTH(l.prefix) + 1) AS INTEGER))
                 FROM lookup_master x
                WHERE UPPER(TRIM(x.lookup_type)) = UPPER(TRIM(l.lookup_type))
                  AND UPPER(TRIM(x.lookup_code)) ~ ('^' || l.prefix || '[0-9]+$')
           ), 0) AS base_no
      FROM legacy l
), planned AS (
    SELECT id, lookup_type, old_code,
           prefix || LPAD((base_no + rn)::TEXT, 3, '0') AS new_code
      FROM numbered
)
INSERT INTO lookup_code_alias(lookup_id, lookup_type, alias_code, canonical_code)
SELECT id, lookup_type, old_code, new_code
  FROM planned
ON CONFLICT DO NOTHING;

-- Rewrite only GENxxx rows. Existing CAT/UNT/MAT/... references remain untouched.
WITH legacy AS (
    SELECT lm.id,
           lm.lookup_type,
           CASE UPPER(TRIM(mc.category_code))
             WHEN 'CATEGORY' THEN 'CAT'
             WHEN 'UNIT' THEN 'UNT'
             WHEN 'UOM' THEN 'UNT'
             WHEN 'MATERIAL' THEN 'MAT'
             WHEN 'BRAND' THEN 'BRD'
             WHEN 'GST' THEN 'GST'
             WHEN 'ROLE' THEN 'ROL'
             WHEN 'DISCOUNT' THEN 'DSC'
             WHEN 'GST_TYPE' THEN 'GTP'
             WHEN 'TRANSPORTER' THEN 'TRN'
             WHEN 'PAYMENT_TERMS' THEN 'PTM'
             WHEN 'CHARGES' THEN 'CHG'
             WHEN 'PAYMENT_MODE' THEN 'PMD'
             WHEN 'EXPENSE_CATEGORY' THEN 'EXP'
             WHEN 'BANK_ACCOUNT' THEN 'BNK'
             WHEN 'QUOTATION_SOURCE' THEN 'QTS'
             WHEN 'REFERENCE_FORMAT' THEN 'RFM'
             ELSE SUBSTRING(REGEXP_REPLACE(UPPER(TRIM(mc.category_code)), '[^A-Z0-9]', '', 'g') || 'MST' FROM 1 FOR 3)
           END AS prefix
      FROM lookup_master lm
      JOIN master_category mc ON UPPER(TRIM(mc.category_name)) = UPPER(TRIM(lm.lookup_type))
     WHERE UPPER(TRIM(lm.lookup_code)) ~ '^GEN[0-9]+$'
), numbered AS (
    SELECT l.*,
           ROW_NUMBER() OVER (PARTITION BY UPPER(TRIM(l.lookup_type)), l.prefix ORDER BY l.id) AS rn,
           COALESCE((
               SELECT MAX(CAST(SUBSTRING(UPPER(TRIM(x.lookup_code)) FROM LENGTH(l.prefix) + 1) AS INTEGER))
                 FROM lookup_master x
                WHERE UPPER(TRIM(x.lookup_type)) = UPPER(TRIM(l.lookup_type))
                  AND UPPER(TRIM(x.lookup_code)) ~ ('^' || l.prefix || '[0-9]+$')
           ), 0) AS base_no
      FROM legacy l
), planned AS (
    SELECT id, prefix || LPAD((base_no + rn)::TEXT, 3, '0') AS new_code
      FROM numbered
)
UPDATE lookup_master lm
   SET lookup_code = p.new_code
  FROM planned p
 WHERE lm.id = p.id;

-- Keep alias metadata synchronized if a later migration safely changes a canonical code.
UPDATE lookup_code_alias a
   SET canonical_code = lm.lookup_code,
       lookup_type = lm.lookup_type
  FROM lookup_master lm
 WHERE lm.id = a.lookup_id;

INSERT INTO application_setting(setting_key, setting_value)
VALUES('auth.selfRegistrationRole', 'SALES')
ON CONFLICT (setting_key) DO NOTHING;
