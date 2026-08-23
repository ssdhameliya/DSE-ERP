-- DSE ERP 8.5.0: canonical role/MFA policy, approval lifecycle and exact notification navigation.

-- Role Master is the only source of role codes used by Login and User Access.
INSERT INTO roles(role_name,description,active) VALUES('SALES','Standard sales and operational access',1)
ON CONFLICT (role_name) DO UPDATE SET active=1;
UPDATE users SET role='SALES' WHERE role_id IN (SELECT id FROM roles WHERE UPPER(role_name) IN ('SALE','USER'));
UPDATE roles SET active=0 WHERE UPPER(role_name) IN ('SALE','USER') AND UPPER(role_name)<>'SALES';

UPDATE users SET role='SALES' WHERE UPPER(COALESCE(role,'')) IN ('SALE','USER');
UPDATE users u SET role_id=r.id, role=r.role_name FROM roles r WHERE UPPER(COALESCE(u.role,''))=UPPER(r.role_name) AND r.active=1;
UPDATE users SET mfa_enabled=CASE WHEN UPPER(COALESCE(role,''))='ADMIN' THEN 0 ELSE 1 END;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS inventory_posted BOOLEAN;
UPDATE sales_header SET inventory_posted=CASE WHEN UPPER(COALESCE(document_status,'')) IN ('DELETED','CANCELLED') THEN FALSE ELSE TRUE END WHERE inventory_posted IS NULL;
ALTER TABLE sales_header ALTER COLUMN inventory_posted SET DEFAULT FALSE;
ALTER TABLE sales_header ALTER COLUMN inventory_posted SET NOT NULL;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_requested_by VARCHAR(120);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_requested_at TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approved_by VARCHAR(120);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approved_at TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS requested_document_status VARCHAR(40);
UPDATE sales_header SET approval_status='APPROVED' WHERE approval_status IS NULL OR TRIM(approval_status)='';
UPDATE sales_header SET requested_document_status=COALESCE(NULLIF(document_status,''),'PENDING') WHERE requested_document_status IS NULL;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_requested_by VARCHAR(120);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_requested_at TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approved_by VARCHAR(120);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approved_at TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS requested_document_status VARCHAR(40);
UPDATE purchase_header SET approval_status='APPROVED' WHERE approval_status IS NULL OR TRIM(approval_status)='';
UPDATE purchase_header SET requested_document_status=COALESCE(NULLIF(document_status,''),'COMPLETED') WHERE requested_document_status IS NULL;

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS module_key VARCHAR(80);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS record_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_code VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_sales_approval_status ON sales_header(approval_status, invoice_date);
CREATE INDEX IF NOT EXISTS idx_purchase_approval_status ON purchase_header(approval_status, invoice_date);
CREATE INDEX IF NOT EXISTS idx_notifications_navigation ON notifications(module_key, record_id, created_at DESC);
