-- Fixed application roles. SALES is the stable database code displayed as "Sale" in the UI.
INSERT INTO roles(role_name,description,active)
VALUES ('ADMIN','Full application access',1),
       ('MANAGER','Business management access',1),
       ('SALES','Sales and operational access',1)
ON CONFLICT (role_name) DO UPDATE SET active=1;

UPDATE users
SET role='SALES', role_id=(SELECT id FROM roles WHERE role_name='SALES')
WHERE UPPER(COALESCE(role,'')) IN ('SALE','USER','VIEWER')
   OR role_id IN (SELECT id FROM roles WHERE role_name IN ('SALE','USER','VIEWER'));

UPDATE users
SET role_id=(SELECT id FROM roles WHERE role_name=UPPER(users.role))
WHERE UPPER(COALESCE(role,'')) IN ('ADMIN','MANAGER','SALES')
  AND role_id IS DISTINCT FROM (SELECT id FROM roles WHERE role_name=UPPER(users.role));

-- Retire only legacy aliases. Custom roles are preserved for the later Role Master migration.
UPDATE roles SET active=0 WHERE role_name IN ('SALE','USER','VIEWER','ADMINISTRATOR');

-- PostgreSQL REAL is approximate. Financial columns use exact decimal storage;
-- quantities retain four decimal places for fractional inventory units.
-- These plain statements intentionally avoid PostgreSQL DO blocks because the
-- Spring SQL initializer splits scripts on semicolons inside procedural blocks.
ALTER TABLE item_master ALTER COLUMN gst TYPE NUMERIC(19,2) USING ROUND(gst::numeric,2);
ALTER TABLE item_master ALTER COLUMN discount_percent TYPE NUMERIC(19,2) USING ROUND(discount_percent::numeric,2);
ALTER TABLE item_master ALTER COLUMN purchase_price TYPE NUMERIC(19,2) USING ROUND(purchase_price::numeric,2);
ALTER TABLE item_master ALTER COLUMN selling_price TYPE NUMERIC(19,2) USING ROUND(selling_price::numeric,2);
ALTER TABLE item_master ALTER COLUMN opening_stock TYPE NUMERIC(19,4) USING ROUND(opening_stock::numeric,4);
ALTER TABLE item_master ALTER COLUMN minimum_stock TYPE NUMERIC(19,4) USING ROUND(minimum_stock::numeric,4);
ALTER TABLE item_master ALTER COLUMN reserved_stock TYPE NUMERIC(19,4) USING ROUND(reserved_stock::numeric,4);
ALTER TABLE party_master ALTER COLUMN opening_balance TYPE NUMERIC(19,2) USING ROUND(opening_balance::numeric,2);
ALTER TABLE sales_header ALTER COLUMN subtotal TYPE NUMERIC(19,2) USING ROUND(subtotal::numeric,2);
ALTER TABLE sales_header ALTER COLUMN gst_amount TYPE NUMERIC(19,2) USING ROUND(gst_amount::numeric,2);
ALTER TABLE sales_header ALTER COLUMN total_amount TYPE NUMERIC(19,2) USING ROUND(total_amount::numeric,2);
ALTER TABLE sales_header ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING ROUND(discount_amount::numeric,2);
ALTER TABLE sales_header ALTER COLUMN charge_amount TYPE NUMERIC(19,2) USING ROUND(charge_amount::numeric,2);
ALTER TABLE sales_header ALTER COLUMN paid_amount TYPE NUMERIC(19,2) USING ROUND(paid_amount::numeric,2);
ALTER TABLE sales_line ALTER COLUMN quantity TYPE NUMERIC(19,4) USING ROUND(quantity::numeric,4);
ALTER TABLE sales_line ALTER COLUMN rate TYPE NUMERIC(19,2) USING ROUND(rate::numeric,2);
ALTER TABLE sales_line ALTER COLUMN gst_percent TYPE NUMERIC(19,2) USING ROUND(gst_percent::numeric,2);
ALTER TABLE sales_line ALTER COLUMN discount_percent TYPE NUMERIC(19,2) USING ROUND(discount_percent::numeric,2);
ALTER TABLE sales_line ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING ROUND(discount_amount::numeric,2);
ALTER TABLE sales_line ALTER COLUMN line_total TYPE NUMERIC(19,2) USING ROUND(line_total::numeric,2);
ALTER TABLE purchase_header ALTER COLUMN subtotal TYPE NUMERIC(19,2) USING ROUND(subtotal::numeric,2);
ALTER TABLE purchase_header ALTER COLUMN gst_amount TYPE NUMERIC(19,2) USING ROUND(gst_amount::numeric,2);
ALTER TABLE purchase_header ALTER COLUMN total_amount TYPE NUMERIC(19,2) USING ROUND(total_amount::numeric,2);
ALTER TABLE purchase_header ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING ROUND(discount_amount::numeric,2);
ALTER TABLE purchase_header ALTER COLUMN paid_amount TYPE NUMERIC(19,2) USING ROUND(paid_amount::numeric,2);
ALTER TABLE purchase_line ALTER COLUMN quantity TYPE NUMERIC(19,4) USING ROUND(quantity::numeric,4);
ALTER TABLE purchase_line ALTER COLUMN rate TYPE NUMERIC(19,2) USING ROUND(rate::numeric,2);
ALTER TABLE purchase_line ALTER COLUMN gst_percent TYPE NUMERIC(19,2) USING ROUND(gst_percent::numeric,2);
ALTER TABLE purchase_line ALTER COLUMN discount_percent TYPE NUMERIC(19,2) USING ROUND(discount_percent::numeric,2);
ALTER TABLE purchase_line ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING ROUND(discount_amount::numeric,2);
ALTER TABLE purchase_line ALTER COLUMN line_total TYPE NUMERIC(19,2) USING ROUND(line_total::numeric,2);
ALTER TABLE quotation_header ALTER COLUMN subtotal TYPE NUMERIC(19,2) USING ROUND(subtotal::numeric,2);
ALTER TABLE quotation_header ALTER COLUMN gst_amount TYPE NUMERIC(19,2) USING ROUND(gst_amount::numeric,2);
ALTER TABLE quotation_header ALTER COLUMN total_amount TYPE NUMERIC(19,2) USING ROUND(total_amount::numeric,2);
ALTER TABLE quotation_header ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING ROUND(discount_amount::numeric,2);
ALTER TABLE quotation_line ALTER COLUMN quantity TYPE NUMERIC(19,4) USING ROUND(quantity::numeric,4);
ALTER TABLE quotation_line ALTER COLUMN rate TYPE NUMERIC(19,2) USING ROUND(rate::numeric,2);
ALTER TABLE quotation_line ALTER COLUMN gst_percent TYPE NUMERIC(19,2) USING ROUND(gst_percent::numeric,2);
ALTER TABLE quotation_line ALTER COLUMN discount_percent TYPE NUMERIC(19,2) USING ROUND(discount_percent::numeric,2);
ALTER TABLE quotation_line ALTER COLUMN line_total TYPE NUMERIC(19,2) USING ROUND(line_total::numeric,2);
ALTER TABLE payment_record ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric,2);
ALTER TABLE return_register ALTER COLUMN quantity TYPE NUMERIC(19,4) USING ROUND(quantity::numeric,4);
ALTER TABLE return_register ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric,2);
ALTER TABLE return_register ALTER COLUMN refund_amount TYPE NUMERIC(19,2) USING ROUND(refund_amount::numeric,2);
ALTER TABLE stock_adjustment ALTER COLUMN quantity TYPE NUMERIC(19,4) USING ROUND(quantity::numeric,4);

UPDATE sales_header SET paid_amount=LEAST(GREATEST(COALESCE(paid_amount,0),0),GREATEST(total_amount,0));
UPDATE purchase_header SET paid_amount=LEAST(GREATEST(COALESCE(paid_amount,0),0),GREATEST(total_amount,0));
UPDATE item_master SET opening_stock=GREATEST(COALESCE(opening_stock,0),0);

ALTER TABLE payment_record DROP CONSTRAINT IF EXISTS payment_record_amount_positive;
ALTER TABLE payment_record ADD CONSTRAINT payment_record_amount_positive CHECK (amount > 0) NOT VALID;
ALTER TABLE sales_header DROP CONSTRAINT IF EXISTS sales_paid_amount_valid;
ALTER TABLE sales_header ADD CONSTRAINT sales_paid_amount_valid CHECK (paid_amount >= 0 AND paid_amount <= total_amount) NOT VALID;
ALTER TABLE purchase_header DROP CONSTRAINT IF EXISTS purchase_paid_amount_valid;
ALTER TABLE purchase_header ADD CONSTRAINT purchase_paid_amount_valid CHECK (paid_amount >= 0 AND paid_amount <= total_amount) NOT VALID;
ALTER TABLE item_master DROP CONSTRAINT IF EXISTS item_opening_stock_nonnegative;
ALTER TABLE item_master ADD CONSTRAINT item_opening_stock_nonnegative CHECK (opening_stock >= 0) NOT VALID;

CREATE INDEX IF NOT EXISTS payment_record_document_idx
    ON payment_record(document_type,document_id,payment_date DESC,id DESC);

-- Remove historical lookup duplicates deterministically, then prevent recurrence.
DELETE FROM lookup_master duplicate
USING lookup_master keeper
WHERE duplicate.id > keeper.id
  AND UPPER(TRIM(duplicate.lookup_type))=UPPER(TRIM(keeper.lookup_type))
  AND UPPER(TRIM(duplicate.lookup_code))=UPPER(TRIM(keeper.lookup_code));

DELETE FROM lookup_master duplicate
USING lookup_master keeper
WHERE duplicate.id > keeper.id
  AND UPPER(TRIM(duplicate.lookup_type))=UPPER(TRIM(keeper.lookup_type))
  AND UPPER(TRIM(duplicate.lookup_value))=UPPER(TRIM(keeper.lookup_value));

CREATE UNIQUE INDEX IF NOT EXISTS lookup_master_type_code_unique
    ON lookup_master(UPPER(TRIM(lookup_type)),UPPER(TRIM(lookup_code)))
    WHERE TRIM(COALESCE(lookup_type,''))<>'' AND TRIM(COALESCE(lookup_code,''))<>'';
CREATE UNIQUE INDEX IF NOT EXISTS lookup_master_type_value_unique
    ON lookup_master(UPPER(TRIM(lookup_type)),UPPER(TRIM(lookup_value)))
    WHERE TRIM(COALESCE(lookup_type,''))<>'' AND TRIM(COALESCE(lookup_value,''))<>'';

-- Reconcile cached document totals from the immutable payment ledger.
UPDATE purchase_header header
SET paid_amount=LEAST(GREATEST(header.total_amount,0),GREATEST(COALESCE(header.paid_amount,0),
        COALESCE((SELECT SUM(payment.amount) FROM payment_record payment
                  WHERE payment.document_type='PURCHASE' AND payment.document_id=header.id),0),
        CASE WHEN UPPER(COALESCE(header.payment_status,'')) IN ('PAID','SETTLED') THEN header.total_amount ELSE 0 END));
UPDATE purchase_header SET payment_status=CASE WHEN paid_amount>=total_amount AND total_amount>0 THEN 'PAID'
        WHEN paid_amount>0 THEN 'PARTIAL' ELSE 'PENDING' END;

UPDATE sales_header header
SET paid_amount=LEAST(GREATEST(header.total_amount,0),GREATEST(COALESCE(header.paid_amount,0),
        COALESCE((SELECT SUM(payment.amount) FROM payment_record payment
                  WHERE payment.document_type='SALE' AND payment.document_id=header.id),0),
        CASE WHEN UPPER(COALESCE(header.payment_status,'')) IN ('PAID','SETTLED') THEN header.total_amount ELSE 0 END));
UPDATE sales_header SET payment_status=CASE WHEN paid_amount>=total_amount AND total_amount>0 THEN 'PAID'
        WHEN paid_amount>0 THEN 'PARTIAL' ELSE 'PENDING' END;

