-- v9.0.15: permanent reference-sequence semantics and Quotation Source compatibility.
UPDATE master_category
SET description='Auto-generated reference patterns. YYYY / YY represent year; XX... defines minimum zero-padding and expands automatically as the sequence grows.'
WHERE UPPER(TRIM(category_code))='REFERENCE_FORMAT';

-- Normalize older Master imports that stored the category code in lookup_type instead of category_name.
UPDATE lookup_master l
SET lookup_type=c.category_name
FROM master_category c
WHERE UPPER(TRIM(c.category_code))='QUOTATION_SOURCE'
  AND UPPER(TRIM(l.lookup_type))=UPPER(TRIM(c.category_code))
  AND UPPER(TRIM(l.lookup_type))<>UPPER(TRIM(c.category_name));
