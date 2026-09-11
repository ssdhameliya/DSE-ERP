-- Account Type master used by Settings -> Payment & Bank and Document Studio.
-- Idempotent seeds preserve user-managed values while guaranteeing a usable baseline.

INSERT INTO master_category(category_code, category_name, description, display_order, is_active)
SELECT 'ACCOUNT_TYPE','ACCOUNT TYPE','Bank account types used by Settings, Excel Studio and PDF Studio',155,1
WHERE NOT EXISTS (
    SELECT 1 FROM master_category
    WHERE UPPER(TRIM(category_code))='ACCOUNT_TYPE'
       OR UPPER(TRIM(category_name))='ACCOUNT TYPE'
);

INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT c.category_name,'ACT001','Savings','Savings bank account',10,1
FROM master_category c
WHERE UPPER(TRIM(c.category_code))='ACCOUNT_TYPE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master lm
      WHERE UPPER(TRIM(lm.lookup_type))=UPPER(TRIM(c.category_name))
        AND (UPPER(TRIM(lm.lookup_code))='ACT001' OR UPPER(TRIM(lm.lookup_value))='SAVINGS')
  );

INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT c.category_name,'ACT002','Current','Current bank account',20,1
FROM master_category c
WHERE UPPER(TRIM(c.category_code))='ACCOUNT_TYPE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master lm
      WHERE UPPER(TRIM(lm.lookup_type))=UPPER(TRIM(c.category_name))
        AND (UPPER(TRIM(lm.lookup_code))='ACT002' OR UPPER(TRIM(lm.lookup_value))='CURRENT')
  );

INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT c.category_name,'ACT003','Personal','Personal bank account',30,1
FROM master_category c
WHERE UPPER(TRIM(c.category_code))='ACCOUNT_TYPE'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master lm
      WHERE UPPER(TRIM(lm.lookup_type))=UPPER(TRIM(c.category_name))
        AND (UPPER(TRIM(lm.lookup_code))='ACT003' OR UPPER(TRIM(lm.lookup_value))='PERSONAL')
  );

-- Stable numbering for any additional Account Type values users add later in Master Data.
INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT c.category_name,'REF_LOOKUP_ACCOUNT_TYPE','ACTXXX','Account Type Master code reference',345,1
FROM master_category c
WHERE UPPER(TRIM(c.category_code))='REFERENCE_FORMAT'
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master lm
      WHERE UPPER(TRIM(lm.lookup_type))=UPPER(TRIM(c.category_name))
        AND UPPER(TRIM(lm.lookup_code))='REF_LOOKUP_ACCOUNT_TYPE'
  );
