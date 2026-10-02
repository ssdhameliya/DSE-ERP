-- 10.0.27 performance support indexes. Safe and idempotent for PostgreSQL.
CREATE INDEX IF NOT EXISTS idx_sales_header_status_date
    ON sales_header(document_status, invoice_date DESC);

CREATE INDEX IF NOT EXISTS idx_sales_header_customer_status
    ON sales_header(customer_id, document_status);

CREATE INDEX IF NOT EXISTS idx_purchase_header_status_date
    ON purchase_header(document_status, invoice_date DESC);

CREATE INDEX IF NOT EXISTS idx_purchase_header_supplier_status
    ON purchase_header(supplier_id, document_status);

CREATE INDEX IF NOT EXISTS idx_quotation_header_status_date
    ON quotation_header(status, quotation_date DESC);

CREATE INDEX IF NOT EXISTS idx_quotation_header_customer_status
    ON quotation_header(customer_id, status);
