-- DSE ERP 5.1.2 server-owned PostgreSQL schema
-- Idempotent migration applied by Spring before JPA initializes.
CREATE TABLE IF NOT EXISTS users
            (
                id BIGSERIAL PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                full_name TEXT,
                role TEXT NOT NULL DEFAULT 'SALES',
                email TEXT UNIQUE,
                active INTEGER NOT NULL DEFAULT 1
            );

CREATE TABLE IF NOT EXISTS item_master
            (
                id BIGSERIAL PRIMARY KEY,
                item_code TEXT UNIQUE,
                description TEXT,
                category TEXT,
                brand TEXT,
                material TEXT,
                size TEXT,
                unit TEXT,
                hsn TEXT,
                gst REAL,
                discount_percent REAL NOT NULL DEFAULT 0,
                purchase_price REAL,
                selling_price REAL,
                opening_stock REAL,
                minimum_stock REAL,
                location TEXT,
                remarks TEXT
            );

CREATE TABLE IF NOT EXISTS stock_adjustment (
                id BIGSERIAL PRIMARY KEY,
                item_code TEXT NOT NULL,
                adjustment_date TEXT NOT NULL,
                adjustment_type TEXT NOT NULL,
                quantity REAL NOT NULL,
                reason TEXT NOT NULL,
                reference_no TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );

CREATE INDEX IF NOT EXISTS idx_stock_adjustment_item ON stock_adjustment(item_code, adjustment_date);

CREATE TABLE IF NOT EXISTS lookup_master (
                id BIGSERIAL PRIMARY KEY,
                lookup_type TEXT NOT NULL,
                lookup_code TEXT NOT NULL,
                lookup_value TEXT NOT NULL,
                description TEXT,
                display_order INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

ALTER TABLE lookup_master ADD COLUMN IF NOT EXISTS created_at TEXT;

CREATE TABLE IF NOT EXISTS master_category (
                id BIGSERIAL PRIMARY KEY,
                category_code TEXT NOT NULL UNIQUE,
                category_name TEXT NOT NULL UNIQUE,
                description TEXT,
                display_order INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

INSERT INTO master_category(category_code, category_name, display_order)
            SELECT DISTINCT UPPER(TRIM(lookup_type)), UPPER(TRIM(lookup_type)), 0
            FROM lookup_master WHERE TRIM(COALESCE(lookup_type,'')) <> '' ON CONFLICT DO NOTHING;

UPDATE lookup_master SET created_at=CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS role_id INTEGER;

CREATE TABLE IF NOT EXISTS roles(id BIGSERIAL PRIMARY KEY, role_name TEXT NOT NULL UNIQUE, description TEXT, active INTEGER NOT NULL DEFAULT 1);

INSERT INTO roles(role_name,description) VALUES('ADMIN','Full application access'),('MANAGER','Business management access'),('SALES','Standard sales and operational access'),('USER','Legacy standard operational access') ON CONFLICT (role_name) DO NOTHING;

UPDATE users SET role='ADMIN',role_id=(SELECT id FROM roles WHERE role_name='ADMIN') WHERE UPPER(role)='ADMINISTRATOR' OR role_id IN (SELECT id FROM roles WHERE role_name='ADMINISTRATOR');

UPDATE users SET role='SALES',role_id=(SELECT id FROM roles WHERE role_name='SALES') WHERE UPPER(role)='USER' OR role_id IN (SELECT id FROM roles WHERE role_name='USER');

UPDATE users SET role_id=(SELECT id FROM roles WHERE role_name=CASE UPPER(role) WHEN 'ADMIN' THEN 'ADMIN' WHEN 'MANAGER' THEN 'MANAGER' WHEN 'SALES' THEN 'SALES' ELSE 'SALES' END) WHERE role_id IS NULL;
UPDATE users SET role='SALES' WHERE role IS NULL OR TRIM(role)='';
UPDATE users SET role_id=(SELECT id FROM roles WHERE role_name='SALES') WHERE role_id IS NULL;

UPDATE roles SET active=0 WHERE role_name IN ('ADMINISTRATOR','USER');

CREATE TABLE IF NOT EXISTS party_master (
                id BIGSERIAL PRIMARY KEY,
                party_type TEXT NOT NULL,
                party_code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                contact_person TEXT,
                phone TEXT,
                email TEXT,
                gstin TEXT,
                address TEXT,
                opening_balance REAL DEFAULT 0,
                is_active INTEGER DEFAULT 1
            );

CREATE TABLE IF NOT EXISTS purchase_header (
                id BIGSERIAL PRIMARY KEY,
                invoice_no TEXT NOT NULL UNIQUE,
                invoice_date TEXT NOT NULL,
                supplier_id INTEGER NOT NULL,
                subtotal REAL NOT NULL,
                gst_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                remarks TEXT,
                FOREIGN KEY(supplier_id) REFERENCES party_master(id)
            );

CREATE TABLE IF NOT EXISTS purchase_line (
                id BIGSERIAL PRIMARY KEY,
                purchase_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL,
                discount_percent REAL NOT NULL DEFAULT 0,
                discount_amount REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(purchase_id) REFERENCES purchase_header(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );

CREATE TABLE IF NOT EXISTS sales_header (
                id BIGSERIAL PRIMARY KEY,
                invoice_no TEXT NOT NULL UNIQUE,
                invoice_date TEXT NOT NULL,
                customer_id INTEGER NOT NULL,
                subtotal REAL NOT NULL,
                gst_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                remarks TEXT,
                FOREIGN KEY(customer_id) REFERENCES party_master(id)
            );

CREATE TABLE IF NOT EXISTS sales_line (
                id BIGSERIAL PRIMARY KEY,
                sales_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL,
                discount_percent REAL NOT NULL DEFAULT 0,
                discount_amount REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(sales_id) REFERENCES sales_header(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );


CREATE TABLE IF NOT EXISTS quotation_header (
                id BIGSERIAL PRIMARY KEY,
                quotation_no TEXT NOT NULL UNIQUE,
                quotation_date TEXT NOT NULL,
                valid_until TEXT,
                customer_id INTEGER NOT NULL,
                subtotal REAL NOT NULL DEFAULT 0,
                gst_amount REAL NOT NULL DEFAULT 0,
                total_amount REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'DRAFT',
                remarks TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(customer_id) REFERENCES party_master(id)
            );

CREATE TABLE IF NOT EXISTS quotation_line (
                id BIGSERIAL PRIMARY KEY,
                quotation_id INTEGER NOT NULL,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                rate REAL NOT NULL,
                gst_percent REAL NOT NULL DEFAULT 0,
                line_total REAL NOT NULL,
                FOREIGN KEY(quotation_id) REFERENCES quotation_header(id) ON DELETE CASCADE,
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );

CREATE TABLE IF NOT EXISTS return_register (
                id BIGSERIAL PRIMARY KEY,
                return_no TEXT NOT NULL UNIQUE,
                return_type TEXT NOT NULL,
                return_date TEXT NOT NULL,
                invoice_no TEXT,
                party_id INTEGER,
                item_code TEXT NOT NULL,
                quantity REAL NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                reason TEXT,
                status TEXT NOT NULL DEFAULT 'COMPLETED',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(party_id) REFERENCES party_master(id),
                FOREIGN KEY(item_code) REFERENCES item_master(item_code)
            );

CREATE TABLE IF NOT EXISTS finance_register (
                id BIGSERIAL PRIMARY KEY,
                voucher_no TEXT NOT NULL UNIQUE,
                voucher_type TEXT NOT NULL,
                voucher_date TEXT NOT NULL,
                party_id INTEGER,
                category TEXT,
                reference_no TEXT,
                amount REAL NOT NULL,
                payment_mode TEXT,
                notes TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(party_id) REFERENCES party_master(id)
            );

CREATE TABLE IF NOT EXISTS reminder_register (
                id BIGSERIAL PRIMARY KEY,
                title TEXT NOT NULL,
                reference_no TEXT,
                due_date TEXT NOT NULL,
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                notes TEXT,
                status TEXT NOT NULL DEFAULT 'OPEN',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

CREATE TABLE IF NOT EXISTS payment_record (
                id BIGSERIAL PRIMARY KEY,
                document_type TEXT NOT NULL,
                document_id INTEGER NOT NULL,
                payment_date TEXT NOT NULL,
                amount REAL NOT NULL,
                payment_mode TEXT NOT NULL,
                reference_no TEXT,
                notes TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

CREATE TABLE IF NOT EXISTS communication_log (
                id BIGSERIAL PRIMARY KEY,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                channel TEXT NOT NULL,
                recipient TEXT,
                subject TEXT,
                status TEXT NOT NULL,
                error_message TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

CREATE TABLE IF NOT EXISTS saved_filter (
                id BIGSERIAL PRIMARY KEY,
                user_id INTEGER,
                screen_key TEXT NOT NULL,
                view_name TEXT NOT NULL,
                filter_json TEXT NOT NULL,
                is_default INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, screen_key, view_name),
                FOREIGN KEY(user_id) REFERENCES users(id)
            );

CREATE TABLE IF NOT EXISTS activity_log (
                id BIGSERIAL PRIMARY KEY,
                entity_type TEXT NOT NULL,
                entity_id INTEGER,
                action TEXT NOT NULL,
                detail TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

CREATE TABLE IF NOT EXISTS document_note (
                id BIGSERIAL PRIMARY KEY,
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                note_text TEXT NOT NULL,
                created_by TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

CREATE INDEX IF NOT EXISTS idx_payment_date ON payment_record(document_type, document_id, payment_date DESC);

CREATE TABLE IF NOT EXISTS permissions (
                id BIGSERIAL PRIMARY KEY,
                permission_key TEXT NOT NULL UNIQUE,
                module_name TEXT NOT NULL,
                action_name TEXT NOT NULL,
                description TEXT,
                active INTEGER NOT NULL DEFAULT 1
            );

CREATE TABLE IF NOT EXISTS role_permission (
                role_id INTEGER NOT NULL,
                permission_id INTEGER NOT NULL,
                allowed INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(role_id, permission_id),
                FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE,
                FOREIGN KEY(permission_id) REFERENCES permissions(id) ON DELETE CASCADE
            );

-- v8.5.1 retains role_code as the compatibility column name; runtime values are derived from ROLE lookup_value.
-- Keep the fresh-database
-- legacy shape that older one-time migrations still expect before V8_5_1 runs.
ALTER TABLE role_permission ADD COLUMN IF NOT EXISTS role_code TEXT;

-- Compatibility seed: keep both the historical role_id and the canonical role_code.
-- Generic ON CONFLICT remains valid after v8.5.1 replaces the legacy role_id PK with
-- the Role Master code uniqueness rule, so this always-run base schema stays idempotent.
INSERT INTO role_permission(role_id, role_code, permission_id, allowed)
            SELECT r.id, UPPER(TRIM(r.role_name)), p.id,
                   CASE
                     WHEN r.role_name='ADMIN' THEN 1
                     WHEN r.role_name='MANAGER' AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
                     WHEN r.role_name='SALES' AND p.action_name IN ('VIEW','CREATE','EDIT')
                          AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
                     ELSE 0
                   END
            FROM roles r CROSS JOIN permissions p ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_role_permission_role ON role_permission(role_id, allowed);

CREATE TABLE IF NOT EXISTS application_setting (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

INSERT INTO application_setting(setting_key,setting_value) VALUES('backup.schedule','MANUAL') ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO application_setting(setting_key,setting_value) VALUES('backup.retention','30') ON CONFLICT (setting_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS backup_history (
                id BIGSERIAL PRIMARY KEY,
                file_name TEXT NOT NULL UNIQUE,
                original_name TEXT,
                source_type TEXT NOT NULL DEFAULT 'MANUAL',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                file_size INTEGER NOT NULL DEFAULT 0,
                integrity_status TEXT NOT NULL DEFAULT 'AVAILABLE',
                schema_version INTEGER,
                application_id TEXT,
                created_by TEXT
            );

CREATE INDEX IF NOT EXISTS idx_backup_history_created ON backup_history(created_at DESC);

CREATE TABLE IF NOT EXISTS application_metadata (
                metadata_key TEXT PRIMARY KEY,
                metadata_value TEXT NOT NULL
            );

CREATE TABLE IF NOT EXISTS notifications (
                id BIGSERIAL PRIMARY KEY,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                severity TEXT NOT NULL DEFAULT 'INFO',
                is_read INTEGER NOT NULL DEFAULT 0,
                target_fxml TEXT,
                reference_no TEXT,
                created_at INTEGER NOT NULL
            );

ALTER TABLE notifications ALTER COLUMN created_at TYPE BIGINT;

CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(is_read, created_at);

CREATE INDEX IF NOT EXISTS idx_sales_date ON sales_header(invoice_date);

CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales_header(customer_id);


CREATE INDEX IF NOT EXISTS idx_quote_date ON quotation_header(quotation_date);

CREATE INDEX IF NOT EXISTS idx_quote_status ON quotation_header(status, valid_until);

CREATE INDEX IF NOT EXISTS idx_payment_document ON payment_record(document_type, document_id);

CREATE INDEX IF NOT EXISTS idx_reminder_due ON reminder_register(status, due_date);

CREATE INDEX IF NOT EXISTS idx_activity_entity ON activity_log(entity_type, entity_id);

ALTER TABLE return_register DROP CONSTRAINT IF EXISTS return_register_return_no_key;

CREATE INDEX IF NOT EXISTS idx_return_number ON return_register(return_no);

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('ROLE','ROLE','Application roles used by login, user access and permission assignment',35,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('DISCOUNT','DISCOUNT','Default item discount percentages',60,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('GST_TYPE','GST TYPE','Sales tax treatment used by Create Sale',70,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('TRANSPORTER','TRANSPORTER','Transporter master used by Create Sale',80,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('PAYMENT_TERMS','PAYMENT TERMS','Payment terms used by Create Sale',100,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('CHARGES','CHARGES','Additional sale charges used by Create Sale',110,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('SALES_INVOICE_FORMAT','SALES INVOICE FORMAT','Sales invoice numbering pattern used by Create Sale',120,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('PAYMENT_MODE','PAYMENT MODE','Payment methods used by Bank & Expense Entry',130,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('EXPENSE_CATEGORY','EXPENSE CATEGORY','Expense classifications used by Expense Entry',140,1) ON CONFLICT DO NOTHING;

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES('BANK_ACCOUNT','BANK ACCOUNT','Bank account master: value is account number and description is bank name',150,1) ON CONFLICT DO NOTHING;

ALTER TABLE communication_log ADD COLUMN IF NOT EXISTS is_read INTEGER NOT NULL DEFAULT 0;

ALTER TABLE item_master ADD COLUMN IF NOT EXISTS reserved_stock REAL NOT NULL DEFAULT 0;

ALTER TABLE item_master ADD COLUMN IF NOT EXISTS is_active INTEGER NOT NULL DEFAULT 1;

ALTER TABLE item_master ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;

ALTER TABLE lookup_master ADD COLUMN IF NOT EXISTS created_at TEXT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1;

ALTER TABLE users ADD COLUMN IF NOT EXISTS role_id INTEGER;

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login TIMESTAMP;

ALTER TABLE users ADD COLUMN IF NOT EXISTS department TEXT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS branch TEXT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS access_level TEXT NOT NULL DEFAULT 'STANDARD';
UPDATE users SET access_level='STANDARD' WHERE access_level IS NULL OR TRIM(access_level)='';
ALTER TABLE users ALTER COLUMN access_level SET DEFAULT 'STANDARD';
ALTER TABLE users ALTER COLUMN access_level SET NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS locked INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;

ALTER TABLE purchase_line ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;

ALTER TABLE sales_line ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS due_date TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS delivery_date TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS paid_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'PENDING';

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS document_status TEXT NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS email_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS warehouse TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS payment_terms TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS currency TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS reference_no TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS gst_treatment TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS transporter TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS lr_awb_no TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS discount_type TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS attachment_path TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS created_by TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS created_at TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS updated_at TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS created_at TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS updated_at TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS email_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS due_date TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS paid_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'PENDING';

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS whatsapp_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS invoice_type TEXT NOT NULL DEFAULT 'TAX INVOICE';

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS salesperson TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS source TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS delivery_address TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS payment_terms TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS transporter TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS reference_no TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS attachment_path TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS document_status TEXT NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS po_date TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS billing_address TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS gst_type TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS door_delivery TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS vehicle_number TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS contact_person TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS transport_note TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS order_no TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS gstin TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS charge_type TEXT;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS charge_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS contact_person_mobile TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS due_date TEXT;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS paid_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'PENDING';

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS email_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS created_at TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS follow_up_date TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS salesperson TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS source TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS created_by TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS discount_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE quotation_line ADD COLUMN IF NOT EXISTS discount_percent REAL NOT NULL DEFAULT 0;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS converted_invoice_no TEXT;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS email_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE quotation_header ADD COLUMN IF NOT EXISTS whatsapp_sent INTEGER NOT NULL DEFAULT 0;

ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS account_name TEXT;

ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS bill_path TEXT;

ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS reconciled INTEGER NOT NULL DEFAULT 0;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS reference_type TEXT;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS party_id INTEGER;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS snoozed_until TEXT;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS completed_at TEXT;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS created_by TEXT;

ALTER TABLE reminder_register ADD COLUMN IF NOT EXISTS updated_at TEXT;

ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS received_from TEXT;

ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS payment_type TEXT NOT NULL DEFAULT 'PARTIAL';

ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS attachment_path TEXT;

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS target_fxml TEXT;

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_no TEXT;

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS refund_amount REAL NOT NULL DEFAULT 0;

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS refund_status TEXT NOT NULL DEFAULT 'PENDING';

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS attachment_path TEXT;

ALTER TABLE return_register ADD COLUMN IF NOT EXISTS updated_at TEXT;

-- Bank reconciliation / payment schema
CREATE TABLE IF NOT EXISTS payment_record (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(20) NOT NULL,
    document_id INTEGER NOT NULL,
    payment_date VARCHAR(10) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    payment_mode VARCHAR(80) NOT NULL,
    reference_no TEXT,
    notes TEXT,
    created_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT (CURRENT_TIMESTAMP::text),
    received_from TEXT,
    payment_type VARCHAR(40) NOT NULL DEFAULT 'PARTIAL',
    attachment_path TEXT
);
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS received_from TEXT;
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS payment_type VARCHAR(40) NOT NULL DEFAULT 'PARTIAL';
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS attachment_path TEXT;

CREATE TABLE IF NOT EXISTS bank_statement_import (
    id BIGSERIAL PRIMARY KEY,
    bank_name VARCHAR(120) NOT NULL,
    bank_account VARCHAR(120) NOT NULL,
    account_holder VARCHAR(200),
    statement_from VARCHAR(10),
    statement_to VARCHAR(10),
    currency VARCHAR(10) DEFAULT 'INR',
    opening_balance NUMERIC(18,2),
    closing_balance NUMERIC(18,2),
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_debit NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_credit NUMERIC(18,2) NOT NULL DEFAULT 0,
    reconciled_count INTEGER NOT NULL DEFAULT 0,
    reconciliation_percent NUMERIC(7,3) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'IMPORTED',
    source_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    source_file_name TEXT,
    source_csv TEXT,
    imported_by VARCHAR(120),
    imported_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

CREATE TABLE IF NOT EXISTS bank_statement_transaction (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES bank_statement_import(id) ON DELETE CASCADE,
    source_row_number INTEGER,
    transaction_timestamp VARCHAR(40),
    transaction_date VARCHAR(10) NOT NULL,
    value_date VARCHAR(10),
    original_description TEXT,
    original_reference TEXT,
    debit_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    balance NUMERIC(18,2),
    status VARCHAR(40) NOT NULL DEFAULT 'UNMATCHED',
    suggested_match_type VARCHAR(20),
    suggested_match_id INTEGER,
    suggested_confidence NUMERIC(7,2),
    notes TEXT,
    transaction_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text,
    updated_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_tx_import ON bank_statement_transaction(import_id, transaction_date, id);
CREATE INDEX IF NOT EXISTS idx_bank_stmt_tx_status ON bank_statement_transaction(status);

CREATE TABLE IF NOT EXISTS bank_reconciliation_allocation (
    id BIGSERIAL PRIMARY KEY,
    statement_transaction_id BIGINT NOT NULL REFERENCES bank_statement_transaction(id),
    target_type VARCHAR(20) NOT NULL,
    target_id INTEGER NOT NULL,
    allocated_amount NUMERIC(18,2) NOT NULL,
    payment_record_id INTEGER,
    finance_entry_id INTEGER,
    created_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text,
    reversed_at VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS bank_reconciliation_audit (
    id BIGSERIAL PRIMARY KEY,
    statement_transaction_id BIGINT NOT NULL REFERENCES bank_statement_transaction(id),
    event_type VARCHAR(60) NOT NULL,
    event_detail TEXT,
    previous_status VARCHAR(40),
    new_status VARCHAR(40),
    performed_by VARCHAR(120),
    created_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

-- Phase 5.1.2 server-owned seed data ----------------------------------------
INSERT INTO permissions(permission_key,module_name,action_name,description)
SELECT m||'.'||a,m,a,a||' access for '||m
FROM unnest(ARRAY['DASHBOARD','SALES','PURCHASE','QUOTATION','INVENTORY','CUSTOMERS','SUPPLIERS','MASTERS','REPORTS','COMMUNICATION','REMINDERS','USERS','BACKUP','SETTINGS','IMPORT']) m
CROSS JOIN unnest(ARRAY['VIEW','CREATE','EDIT','DELETE','APPROVE','EXPORT']) a
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission(role_id,role_code,permission_id,allowed)
SELECT r.id,UPPER(TRIM(r.role_name)),p.id,
       CASE WHEN r.role_name='ADMIN' THEN 1
            WHEN r.role_name='MANAGER' AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
            WHEN r.role_name='SALES' AND p.action_name IN ('VIEW','CREATE','EDIT') AND p.module_name NOT IN ('USERS','BACKUP','SETTINGS') THEN 1
            ELSE 0 END
FROM roles r CROSS JOIN permissions p
ON CONFLICT DO NOTHING;

-- 5.1.19: lookup_master is seeded on every startup, so remove historical duplicates and make future seeds idempotent.
DELETE FROM lookup_master a
USING lookup_master b
WHERE a.id > b.id
  AND UPPER(TRIM(a.lookup_type)) = UPPER(TRIM(b.lookup_type))
  AND UPPER(TRIM(a.lookup_code)) = UPPER(TRIM(b.lookup_code));

DELETE FROM lookup_master a
USING lookup_master b
WHERE a.id > b.id
  AND UPPER(TRIM(a.lookup_type)) = UPPER(TRIM(b.lookup_type))
  AND UPPER(TRIM(a.lookup_value)) = UPPER(TRIM(b.lookup_value));

CREATE UNIQUE INDEX IF NOT EXISTS ux_lookup_master_type_code
ON lookup_master (UPPER(TRIM(lookup_type)), UPPER(TRIM(lookup_code)));
CREATE UNIQUE INDEX IF NOT EXISTS ux_lookup_master_type_value
ON lookup_master (UPPER(TRIM(lookup_type)), UPPER(TRIM(lookup_value)));

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,is_active) VALUES
('CATEGORY','CAT001','Valve',1),('CATEGORY','CAT002','Pipe',1),('CATEGORY','CAT003','Flange',1),
('UNIT','UNT001','Nos',1),('UNIT','UNT002','Kg',1),('UNIT','UNT003','Meter',1),
('MATERIAL','MAT001','SS304',1),('MATERIAL','MAT002','SS316',1),('MATERIAL','MAT003','Carbon Steel',1),
('BRAND','BRD001','L&T',1),('BRAND','BRD002','Kirloskar',1),
('GST','GST001','0',1),('GST','GST002','5',1),('GST','GST003','12',1),('GST','GST004','18',1),('GST','GST005','28',1),
('DISCOUNT','DSC001','0',1),('DISCOUNT','DSC002','2',1),('DISCOUNT','DSC003','5',1),('DISCOUNT','DSC004','10',1),('DISCOUNT','DSC005','15',1),('DISCOUNT','DSC006','20',1),
('ROLE','ADMIN','Admin',1),('ROLE','MANAGER','Manager',1),('ROLE','SALES','Sales',1)
ON CONFLICT DO NOTHING;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT mc.category_name,v.code,v.value,v.description,v.ord,1
FROM master_category mc
JOIN (VALUES
 ('PAYMENT_MODE','PMODE001','NEFT','',10),('PAYMENT_MODE','PMODE002','RTGS','',20),('PAYMENT_MODE','PMODE003','UPI','',30),
 ('PAYMENT_MODE','PMODE004','Cheque','',40),('PAYMENT_MODE','PMODE005','Card','',50),('PAYMENT_MODE','PMODE006','Cash','',60),
 ('PAYMENT_MODE','PMODE007','Bank Transfer','',70),('PAYMENT_MODE','PMODE008','Other','',80),
 ('EXPENSE_CATEGORY','ECAT001','Office Expenses','',10),('EXPENSE_CATEGORY','ECAT002','Travel Expenses','',20),
 ('EXPENSE_CATEGORY','ECAT003','Marketing','',30),('EXPENSE_CATEGORY','ECAT004','Purchase','',40),
 ('EXPENSE_CATEGORY','ECAT005','Internet & Phone','',50),('EXPENSE_CATEGORY','ECAT006','Transport','',60),
 ('EXPENSE_CATEGORY','ECAT007','Maintenance','',70),('EXPENSE_CATEGORY','ECAT008','Utilities','',80),
 ('EXPENSE_CATEGORY','ECAT009','Rent','',90),('EXPENSE_CATEGORY','ECAT010','Salary','',100),('EXPENSE_CATEGORY','ECAT011','Other','',110),
 ('GST_TYPE','GSTT001','GST','Intra-state GST',10),('GST_TYPE','GSTT002','IGST','Inter-state GST',20),
 ('PAYMENT_TERMS','PAY001','Due on Receipt','Immediate payment',10),('PAYMENT_TERMS','PAY002','7 Days','Payment due in 7 days',20),
 ('PAYMENT_TERMS','PAY003','15 Days','Payment due in 15 days',30),('PAYMENT_TERMS','PAY004','30 Days','Payment due in 30 days',40),
 ('PAYMENT_TERMS','PAY005','45 Days','Payment due in 45 days',50),
 ('CHARGES','CHG001','Freight','Freight / transport charges',10),('CHARGES','CHG002','Packing & Forwarding','Packing and forwarding charges',20),
 ('CHARGES','CHG003','Handling','Handling charges',30),
 ('SALES_INVOICE_FORMAT','SIFMT001','IN/DD-MM-YYYY/XXXX','Auto-generated sales invoice number format',10)
) AS v(category_code,code,value,description,ord) ON v.category_code=mc.category_code
WHERE NOT EXISTS (SELECT 1 FROM lookup_master x WHERE x.lookup_code=v.code);

-- Indexes on columns added by compatibility ALTER statements above.
CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_header_order_no ON sales_header(order_no) WHERE order_no IS NOT NULL AND TRIM(order_no) <> '';
CREATE INDEX IF NOT EXISTS idx_sales_due ON sales_header(due_date, payment_status);

-- 5.1.19: reconcile purchase paid amounts from recorded supplier payments and legacy PAID/SETTLED status.
UPDATE purchase_header p
SET paid_amount = LEAST(COALESCE(p.total_amount,0), GREATEST(COALESCE(p.paid_amount,0), x.recorded_paid))
FROM (
    SELECT document_id, COALESCE(SUM(amount),0) recorded_paid
    FROM payment_record
    WHERE UPPER(document_type)='PURCHASE'
    GROUP BY document_id
) x
WHERE p.id=x.document_id
  AND GREATEST(COALESCE(p.paid_amount,0), x.recorded_paid) <> COALESCE(p.paid_amount,0);

UPDATE purchase_header
SET paid_amount = COALESCE(total_amount,0)
WHERE UPPER(COALESCE(payment_status,'')) IN ('PAID','SETTLED')
  AND COALESCE(paid_amount,0) < COALESCE(total_amount,0);

UPDATE purchase_header
SET payment_status = CASE
    WHEN COALESCE(total_amount,0) > 0 AND COALESCE(paid_amount,0) >= COALESCE(total_amount,0) THEN 'PAID'
    WHEN COALESCE(paid_amount,0) > 0 THEN 'PARTIAL'
    ELSE COALESCE(NULLIF(payment_status,''),'PENDING')
END
WHERE COALESCE(total_amount,0) > 0;


-- DSE ERP 8.5.0 base-schema compatibility guards.
-- The versioned migration repeats these idempotently for upgraded databases; keeping
-- them here also makes a brand-new managed PostgreSQL workspace immediately compatible.
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS inventory_posted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_requested_by VARCHAR(120);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approval_requested_at TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approved_by VARCHAR(120);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS approved_at TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS requested_document_status VARCHAR(40);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_requested_by VARCHAR(120);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approval_requested_at TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approved_by VARCHAR(120);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS approved_at TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS requested_document_status VARCHAR(40);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS module_key VARCHAR(80);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS record_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_code VARCHAR(40);
UPDATE users SET role='SALES' WHERE UPPER(COALESCE(role,'')) IN ('SALE','USER');
UPDATE users SET mfa_enabled=CASE WHEN UPPER(COALESCE(role,''))='ADMIN' THEN 0 ELSE 1 END;
