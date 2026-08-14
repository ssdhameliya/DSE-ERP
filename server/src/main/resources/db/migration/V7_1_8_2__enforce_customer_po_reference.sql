-- 7.1.8 follow-up: enforce customer-owned Sales PO references.
-- A previous internal build could already have recorded the _1 migration key,
-- so this separately keyed cleanup is intentionally repeat-safe.
UPDATE sales_header
SET order_no = NULL
WHERE TRIM(COALESCE(order_no, '')) ~ '^PO/[0-9]{2}-[0-9]{2}-[0-9]{4}/[0-9]{4}$';
