-- v7.3.18: Purchase invoice numbering uses the same Master-driven format engine as Sales.
INSERT INTO master_category(category_code, category_name, description, display_order, is_active)
VALUES('PURCHASE_INVOICE_FORMAT','PURCHASE INVOICE FORMAT','Purchase invoice numbering pattern used by Create Purchase',125,1)
ON CONFLICT DO NOTHING;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT mc.category_name,'PIFMT001','PUR/DD-MM-YYYY/XXXX','Auto-generated purchase invoice number format',10,1
FROM master_category mc
WHERE mc.category_code='PURCHASE_INVOICE_FORMAT'
  AND NOT EXISTS (SELECT 1 FROM lookup_master x WHERE x.lookup_code='PIFMT001');
