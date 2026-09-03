-- DSE ERP 9.0.62: Project Execution module removed by product decision.
-- Remove module-owned permissions, workflow tables, and cross-module linkage columns.
DELETE FROM role_permission
WHERE permission_id IN (SELECT id FROM permissions WHERE module_name='PROJECT_EXECUTION' OR permission_key LIKE 'PROJECT_EXECUTION.%');
DELETE FROM permissions WHERE module_name='PROJECT_EXECUTION' OR permission_key LIKE 'PROJECT_EXECUTION.%';

DROP TABLE IF EXISTS workflow_document_line CASCADE;
DROP TABLE IF EXISTS workflow_document CASCADE;

ALTER TABLE sales_header DROP COLUMN IF EXISTS project_no;
ALTER TABLE sales_header DROP COLUMN IF EXISTS sales_order_no;
ALTER TABLE sales_header DROP COLUMN IF EXISTS dispatch_no;
ALTER TABLE sales_header DROP COLUMN IF EXISTS customer_po_no;
ALTER TABLE purchase_header DROP COLUMN IF EXISTS project_no;
ALTER TABLE purchase_header DROP COLUMN IF EXISTS purchase_order_no;
ALTER TABLE purchase_header DROP COLUMN IF EXISTS grn_no;

DELETE FROM lookup_master
WHERE UPPER(TRIM(COALESCE(lookup_code,''))) IN ('REF_PROJECT','REF_SALES_ORDER','REF_PURCHASE_ORDER','REF_GRN','REF_DISPATCH');
