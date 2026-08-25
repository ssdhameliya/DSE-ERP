-- v8.5.8: isolated Purchase Recon domain. Existing Supplier/Purchase tables are intentionally untouched.
CREATE TABLE IF NOT EXISTS purchase_recon_import_batch (
    id                  BIGSERIAL PRIMARY KEY,
    source_file_name    TEXT NOT NULL,
    source_fingerprint  TEXT NOT NULL UNIQUE,
    import_note         TEXT,
    imported_by         TEXT,
    imported_at         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_rows          INTEGER NOT NULL DEFAULT 0,
    imported_rows       INTEGER NOT NULL DEFAULT 0,
    duplicate_rows      INTEGER NOT NULL DEFAULT 0,
    warning_rows        INTEGER NOT NULL DEFAULT 0,
    ignored_rows        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recon_supplier (
    id                  SERIAL PRIMARY KEY,
    recon_supplier_ref  TEXT NOT NULL UNIQUE,
    legal_name          TEXT NOT NULL,
    gstin               TEXT,
    pan                 TEXT,
    contact_person      TEXT,
    phone               TEXT,
    email               TEXT,
    notes               TEXT,
    status              TEXT NOT NULL DEFAULT 'ACTIVE',
    source              TEXT NOT NULL DEFAULT 'MANUAL',
    import_batch_id     BIGINT REFERENCES purchase_recon_import_batch(id),
    attachment_path     TEXT NOT NULL DEFAULT '',
    created_by          TEXT,
    created_at          TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          TEXT,
    updated_at          TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_recon_supplier_gstin
    ON recon_supplier (UPPER(TRIM(gstin)))
    WHERE TRIM(COALESCE(gstin,''))<>'';
CREATE UNIQUE INDEX IF NOT EXISTS ux_recon_supplier_name_without_gstin
    ON recon_supplier ((regexp_replace(UPPER(COALESCE(legal_name,'')), '[^A-Z0-9]', '', 'g')))
    WHERE TRIM(COALESCE(gstin,''))='';
CREATE INDEX IF NOT EXISTS idx_recon_supplier_name
    ON recon_supplier ((regexp_replace(UPPER(COALESCE(legal_name,'')), '[^A-Z0-9]', '', 'g')));

CREATE TABLE IF NOT EXISTS purchase_recon (
    id                      SERIAL PRIMARY KEY,
    recon_ref               TEXT NOT NULL UNIQUE,
    recon_supplier_id       INTEGER NOT NULL REFERENCES recon_supplier(id),
    supplier_name_snapshot  TEXT NOT NULL,
    supplier_gstin_snapshot TEXT,
    supplier_invoice_no     TEXT NOT NULL,
    invoice_date            DATE NOT NULL,
    financial_year          TEXT NOT NULL,
    taxable_value           NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst                    NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst                    NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst                    NUMERIC(18,2) NOT NULL DEFAULT 0,
    other_adjustment        NUMERIC(18,2) NOT NULL DEFAULT 0,
    invoice_value           NUMERIC(18,2) NOT NULL DEFAULT 0,
    linked_amount           NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_difference          NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_review_required     INTEGER NOT NULL DEFAULT 0,
    status                  TEXT NOT NULL DEFAULT 'OPEN',
    source                  TEXT NOT NULL DEFAULT 'MANUAL',
    import_batch_id         BIGINT REFERENCES purchase_recon_import_batch(id),
    source_row              INTEGER,
    notes                   TEXT,
    attachment_path         TEXT NOT NULL DEFAULT '',
    created_by              TEXT,
    created_at              TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              TEXT,
    updated_at              TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_recon_business_key
    ON purchase_recon (recon_supplier_id, UPPER(TRIM(supplier_invoice_no)), financial_year);
CREATE INDEX IF NOT EXISTS idx_purchase_recon_status_date ON purchase_recon (status, invoice_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_purchase_recon_supplier ON purchase_recon (recon_supplier_id, invoice_date DESC);

INSERT INTO permissions(permission_key,module_name,action_name,description) VALUES
('RECON_SUPPLIER.VIEW','RECON_SUPPLIER','VIEW','View Recon Supplier master'),
('RECON_SUPPLIER.CREATE','RECON_SUPPLIER','CREATE','Create Recon Supplier master records'),
('RECON_SUPPLIER.EDIT','RECON_SUPPLIER','EDIT','Edit Recon Supplier master records'),
('PURCHASE_RECON.VIEW','PURCHASE_RECON','VIEW','View Purchase Recon register and details'),
('PURCHASE_RECON.CREATE','PURCHASE_RECON','CREATE','Create Purchase Recon records manually'),
('PURCHASE_RECON.EDIT','PURCHASE_RECON','EDIT','Edit eligible Purchase Recon records and attachments'),
('PURCHASE_RECON.IMPORT','PURCHASE_RECON','IMPORT','Import Purchase Recon workbooks and create missing Recon Suppliers'),
('PURCHASE_RECON.MATCH','PURCHASE_RECON','MATCH','Match Purchase Recon records from Bank Statement')
ON CONFLICT (permission_key) DO UPDATE SET description=EXCLUDED.description, active=1;

-- Purchase-oriented roles get the new isolated recon workspace. Bank matching still additionally requires BANK_EXPENSE.RECONCILE.
INSERT INTO role_permission(role_code,permission_id,allowed)
SELECT 'PURCHASE',p.id,1
FROM permissions p
WHERE p.permission_key IN (
 'RECON_SUPPLIER.VIEW','RECON_SUPPLIER.CREATE','RECON_SUPPLIER.EDIT',
 'PURCHASE_RECON.VIEW','PURCHASE_RECON.CREATE','PURCHASE_RECON.EDIT','PURCHASE_RECON.IMPORT','PURCHASE_RECON.MATCH'
)
ON CONFLICT (UPPER(TRIM(role_code)),permission_id) WHERE TRIM(COALESCE(role_code,''))<>'' DO NOTHING;
