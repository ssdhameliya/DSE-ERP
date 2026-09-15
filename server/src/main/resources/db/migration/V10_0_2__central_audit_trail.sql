-- 10.0.13 centralized business audit trail.
CREATE TABLE IF NOT EXISTS audit_event (
    id BIGSERIAL PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id BIGINT NOT NULL DEFAULT 0,
    reference_no TEXT,
    action TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'BUSINESS_CHANGE',
    detail TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'SERVER',
    legacy_source TEXT,
    legacy_id BIGINT,
    correlation_id TEXT
);

CREATE TABLE IF NOT EXISTS audit_change (
    id BIGSERIAL PRIMARY KEY,
    audit_event_id BIGINT NOT NULL REFERENCES audit_event(id) ON DELETE RESTRICT,
    field_name TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_event_entity ON audit_event(entity_type, entity_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_created ON audit_event(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_user ON audit_event(created_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_action ON audit_event(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_change_event ON audit_change(audit_event_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_event_legacy ON audit_event(legacy_source, legacy_id)
WHERE legacy_source IS NOT NULL AND legacy_id IS NOT NULL;

INSERT INTO permissions(permission_key,module_name,action_name,description) VALUES
('AUDIT.VIEW','AUDIT','VIEW','View record-level business audit trail'),
('AUDIT.GLOBAL','AUDIT','GLOBAL','View the global business audit trail'),
('AUDIT.EXPORT','AUDIT','EXPORT','Export business audit trail')
ON CONFLICT(permission_key) DO UPDATE SET active=1;

INSERT INTO role_permission(role_code,permission_id,allowed)
SELECT 'ADMIN',p.id,1 FROM permissions p WHERE p.permission_key IN ('AUDIT.VIEW','AUDIT.GLOBAL','AUDIT.EXPORT')
ON CONFLICT (UPPER(TRIM(role_code)),permission_id) WHERE TRIM(COALESCE(role_code,''))<>''
DO UPDATE SET allowed=1;

-- Historical activity rows are preserved and normalized. Detailed before/after values
-- were not captured by older versions, so they remain absent rather than fabricated.
INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source,legacy_source,legacy_id)
SELECT UPPER(TRIM(a.entity_type)),COALESCE(a.entity_id,0),
       CASE UPPER(TRIM(a.entity_type))
         WHEN 'SALE' THEN (SELECT invoice_no FROM sales_header WHERE id=a.entity_id)
         WHEN 'PURCHASE' THEN (SELECT invoice_no FROM purchase_header WHERE id=a.entity_id)
         WHEN 'QUOTATION' THEN (SELECT quotation_no FROM quotation_header WHERE id=a.entity_id)
         WHEN 'SALES_RETURN' THEN (SELECT return_no FROM return_register WHERE id=a.entity_id)
         WHEN 'PURCHASE_RETURN' THEN (SELECT return_no FROM return_register WHERE id=a.entity_id)
         WHEN 'PARTY' THEN (SELECT party_code FROM party_master WHERE id=a.entity_id)
         WHEN 'CUSTOMER' THEN (SELECT party_code FROM party_master WHERE id=a.entity_id)
         WHEN 'SUPPLIER' THEN (SELECT party_code FROM party_master WHERE id=a.entity_id)
         WHEN 'ITEM' THEN (SELECT item_code FROM item_master WHERE id=a.entity_id)
         WHEN 'FINANCE' THEN (SELECT voucher_no FROM finance_register WHERE id=a.entity_id)
         WHEN 'PURCHASE_RECON' THEN (SELECT recon_ref FROM purchase_recon WHERE id=a.entity_id)
         WHEN 'RECON_SUPPLIER' THEN (SELECT recon_supplier_ref FROM recon_supplier WHERE id=a.entity_id)
         WHEN 'MASTER_LOOKUP' THEN (SELECT CONCAT(lookup_type,':',lookup_code) FROM lookup_master WHERE id=a.entity_id)
         WHEN 'MASTER_CATEGORY' THEN (SELECT category_code FROM master_category WHERE id=a.entity_id)
         ELSE NULL END,
       UPPER(TRIM(a.action)),
       CASE WHEN UPPER(a.action) LIKE '%PAYMENT%' OR UPPER(a.action) LIKE '%REFUND%' THEN 'FINANCIAL'
            WHEN UPPER(a.action) LIKE '%EMAIL%' OR UPPER(a.action) LIKE '%WHATSAPP%' THEN 'COMMUNICATION'
            WHEN UPPER(a.action) LIKE '%PDF%' OR UPPER(a.action) LIKE '%EXCEL%' OR UPPER(a.action) LIKE '%ATTACH%' THEN 'DOCUMENT'
            WHEN UPPER(a.action) IN ('APPROVED','REJECTED','CANCELLED','DELETED','PENDING APPROVAL','CONVERTED') THEN 'LIFECYCLE'
            ELSE 'BUSINESS_CHANGE' END,
       COALESCE(a.detail,''),COALESCE(NULLIF(a.created_by,''),'System'),COALESCE(a.created_at::text,CURRENT_TIMESTAMP::text),'LEGACY','activity_log',a.id
FROM activity_log a
ON CONFLICT DO NOTHING;

INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source,legacy_source,legacy_id)
SELECT UPPER(TRIM(p.document_type)),p.document_id,
       CASE UPPER(TRIM(p.document_type)) WHEN 'SALE' THEN (SELECT invoice_no FROM sales_header WHERE id=p.document_id) WHEN 'PURCHASE' THEN (SELECT invoice_no FROM purchase_header WHERE id=p.document_id) ELSE NULL END,
       'PAYMENT_RECORDED','FINANCIAL',
       CONCAT(COALESCE(p.payment_mode,'Payment'),' • ',COALESCE(p.amount,0),' • ',COALESCE(p.reference_no,'')),
       COALESCE(NULLIF(p.created_by,''),'System'),COALESCE(p.created_at::text,CURRENT_TIMESTAMP::text),'LEGACY','payment_record',p.id
FROM payment_record p
ON CONFLICT DO NOTHING;

INSERT INTO audit_change(audit_event_id,field_name,old_value,new_value)
SELECT e.id,'Payment Amount',NULL,COALESCE(p.amount,0)::text
FROM audit_event e JOIN payment_record p ON e.legacy_source='payment_record' AND e.legacy_id=p.id
WHERE NOT EXISTS (SELECT 1 FROM audit_change c WHERE c.audit_event_id=e.id AND c.field_name='Payment Amount');

INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source,legacy_source,legacy_id)
SELECT UPPER(TRIM(c.entity_type)),c.entity_id,
       CASE UPPER(TRIM(c.entity_type))
         WHEN 'SALE' THEN (SELECT invoice_no FROM sales_header WHERE id=c.entity_id)
         WHEN 'PURCHASE' THEN (SELECT invoice_no FROM purchase_header WHERE id=c.entity_id)
         WHEN 'QUOTATION' THEN (SELECT quotation_no FROM quotation_header WHERE id=c.entity_id)
         WHEN 'SALES_RETURN' THEN (SELECT return_no FROM return_register WHERE id=c.entity_id)
         WHEN 'PURCHASE_RETURN' THEN (SELECT return_no FROM return_register WHERE id=c.entity_id)
         WHEN 'PARTY' THEN (SELECT party_code FROM party_master WHERE id=c.entity_id)
         WHEN 'CUSTOMER' THEN (SELECT party_code FROM party_master WHERE id=c.entity_id)
         WHEN 'SUPPLIER' THEN (SELECT party_code FROM party_master WHERE id=c.entity_id)
         WHEN 'ITEM' THEN (SELECT item_code FROM item_master WHERE id=c.entity_id)
         ELSE NULL END,
       CASE WHEN UPPER(COALESCE(c.status,'')) IN ('FAILED','ERROR') THEN UPPER(COALESCE(c.channel,'COMMUNICATION'))||'_FAILED' ELSE UPPER(COALESCE(c.channel,'COMMUNICATION'))||'_SENT' END,
       'COMMUNICATION',CONCAT(COALESCE(c.recipient,''),' • ',COALESCE(c.subject,''),' • ',COALESCE(c.status,'')),
       COALESCE(NULLIF(c.created_by,''),'System'),COALESCE(c.created_at::text,CURRENT_TIMESTAMP::text),'LEGACY','communication_log',c.id
FROM communication_log c
ON CONFLICT DO NOTHING;

INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source,legacy_source,legacy_id)
SELECT UPPER(TRIM(d.document_type)),d.document_id,
       CASE UPPER(TRIM(d.document_type))
         WHEN 'SALE' THEN (SELECT invoice_no FROM sales_header WHERE id=d.document_id)
         WHEN 'PURCHASE' THEN (SELECT invoice_no FROM purchase_header WHERE id=d.document_id)
         WHEN 'QUOTATION' THEN (SELECT quotation_no FROM quotation_header WHERE id=d.document_id)
         WHEN 'SALES_RETURN' THEN (SELECT return_no FROM return_register WHERE id=d.document_id)
         WHEN 'PURCHASE_RETURN' THEN (SELECT return_no FROM return_register WHERE id=d.document_id)
         WHEN 'PURCHASE_RECON' THEN (SELECT recon_ref FROM purchase_recon WHERE id=d.document_id)
         WHEN 'PARTY' THEN (SELECT party_code FROM party_master WHERE id=d.document_id)
         WHEN 'CUSTOMER' THEN (SELECT party_code FROM party_master WHERE id=d.document_id)
         WHEN 'SUPPLIER' THEN (SELECT party_code FROM party_master WHERE id=d.document_id)
         WHEN 'ITEM' THEN (SELECT item_code FROM item_master WHERE id=d.document_id)
         ELSE NULL END,
       'ATTACHMENT_ADDED','DOCUMENT',COALESCE(d.file_name,''),COALESCE(NULLIF(d.created_by,''),'System'),COALESCE(d.created_at::text,CURRENT_TIMESTAMP::text),'LEGACY','document_attachment',d.id
FROM document_attachment d
ON CONFLICT DO NOTHING;

-- Repair Sales historically created through Quotation conversion with incomplete Sales fields.
-- The data repair event is created only for rows that were actually incomplete.
WITH converted AS (
    SELECT h.id,h.invoice_no,p.address,p.gstin,
           COALESCE((SELECT setting_value FROM application_setting WHERE setting_key='company.gstin'),'') company_gstin,
           q.quotation_no
    FROM sales_header h
    JOIN quotation_header q ON q.converted_invoice_no=h.invoice_no
    JOIN party_master p ON p.id=h.customer_id
    WHERE NULLIF(BTRIM(COALESCE(h.gst_type,'')),'') IS NULL
), repaired AS (
    UPDATE sales_header h
    SET gst_type=CASE WHEN LENGTH(c.company_gstin)>=2 AND LENGTH(COALESCE(c.gstin,''))>=2 AND LEFT(c.company_gstin,2)<>LEFT(c.gstin,2) THEN 'IGST' ELSE 'GST' END,
        billing_address=COALESCE(NULLIF(h.billing_address,''),c.address,''),
        delivery_address=COALESCE(NULLIF(h.delivery_address,''),c.address,''),
        billing_gstin=COALESCE(NULLIF(h.billing_gstin,''),c.gstin,''),
        delivery_gstin=COALESCE(NULLIF(h.delivery_gstin,''),c.gstin,''),
        gstin=COALESCE(NULLIF(h.gstin,''),c.gstin,''),
        same_as_billing=TRUE,
        reference_no=COALESCE(NULLIF(h.reference_no,''),c.quotation_no)
    FROM converted c WHERE h.id=c.id
    RETURNING h.id,h.invoice_no,h.gst_type
)
INSERT INTO audit_event(entity_type,entity_id,reference_no,action,category,detail,created_by,created_at,source,legacy_source,legacy_id)
SELECT 'SALE',r.id,r.invoice_no,'DATA_REPAIRED','BUSINESS_CHANGE','Completed missing Sale fields from linked quotation/customer during 10.0.13 migration','System Migration',CURRENT_TIMESTAMP::text,'MIGRATION','quotation_sale_repair',r.id
FROM repaired r
ON CONFLICT DO NOTHING;

INSERT INTO audit_change(audit_event_id,field_name,old_value,new_value)
SELECT e.id,'GST Type','',COALESCE(h.gst_type,'')
FROM audit_event e JOIN sales_header h ON h.id=e.entity_id
WHERE e.legacy_source='quotation_sale_repair'
  AND NOT EXISTS (SELECT 1 FROM audit_change c WHERE c.audit_event_id=e.id AND c.field_name='GST Type');
