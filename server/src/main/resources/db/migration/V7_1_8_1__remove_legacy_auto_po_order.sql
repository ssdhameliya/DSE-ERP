-- 7.1.8 follow-up: Sales customer PO Order No. is user-entered and optional.
-- Earlier builds generated PO/DD-MM-YYYY/XXXX when the user left it blank.
-- Clear only that recognizable legacy generated pattern and preserve all other
-- customer-entered PO references.

UPDATE sales_header
SET order_no = NULL
WHERE TRIM(COALESCE(order_no, '')) ~ '^PO/[0-9]{2}-[0-9]{2}-[0-9]{4}/[0-9]{4}$';
