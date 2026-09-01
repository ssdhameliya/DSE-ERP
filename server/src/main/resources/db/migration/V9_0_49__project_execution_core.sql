CREATE TABLE IF NOT EXISTS workflow_document (
    id SERIAL PRIMARY KEY,
    document_type VARCHAR(32) NOT NULL,
    document_no VARCHAR(80) NOT NULL,
    document_date DATE NOT NULL DEFAULT CURRENT_DATE,
    project_no VARCHAR(80),
    parent_no VARCHAR(80),
    party_name VARCHAR(240),
    customer_po_no VARCHAR(120),
    expected_date DATE,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_document_type_no UNIQUE(document_type, document_no),
    CONSTRAINT ck_workflow_document_total_nonnegative CHECK(total_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_workflow_document_type_date ON workflow_document(document_type, document_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_document_project ON workflow_document(project_no);
CREATE INDEX IF NOT EXISTS idx_workflow_document_parent ON workflow_document(parent_no);

CREATE TABLE IF NOT EXISTS workflow_document_line (
    id SERIAL PRIMARY KEY,
    document_id INTEGER NOT NULL REFERENCES workflow_document(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    item_code VARCHAR(120),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    rate NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_document_line UNIQUE(document_id,line_no),
    CONSTRAINT ck_workflow_line_quantity_nonnegative CHECK(quantity >= 0),
    CONSTRAINT ck_workflow_line_rate_nonnegative CHECK(rate >= 0),
    CONSTRAINT ck_workflow_line_amount_nonnegative CHECK(amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_workflow_line_document ON workflow_document_line(document_id,line_no);

ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS project_no VARCHAR(80);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS sales_order_no VARCHAR(80);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS dispatch_no VARCHAR(80);
ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS customer_po_no VARCHAR(120);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS project_no VARCHAR(80);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS purchase_order_no VARCHAR(80);
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS grn_no VARCHAR(80);


-- Project Execution permissions are managed centrally through the existing permission matrix.
-- ADMIN continues to bypass role rows by design; other roles receive access only when explicitly assigned.
INSERT INTO permissions(permission_key,module_name,action_name,description) VALUES
('PROJECT_EXECUTION.VIEW','PROJECT_EXECUTION','VIEW','View Projects, Sales Orders, Purchase Orders, GRNs and Dispatches'),
('PROJECT_EXECUTION.CREATE','PROJECT_EXECUTION','CREATE','Create Project Execution records'),
('PROJECT_EXECUTION.EDIT','PROJECT_EXECUTION','EDIT','Edit Project Execution records'),
('PROJECT_EXECUTION.DELETE','PROJECT_EXECUTION','DELETE','Delete Project Execution records'),
('PROJECT_EXECUTION.EXPORT','PROJECT_EXECUTION','EXPORT','Export Project Execution information')
ON CONFLICT (permission_key) DO UPDATE SET description=EXCLUDED.description, active=1;
