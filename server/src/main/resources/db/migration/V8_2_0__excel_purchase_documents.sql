-- v8.2.0 consolidated ERP document enhancements. Additive only.

CREATE TABLE IF NOT EXISTS document_attachment (
    id              BIGSERIAL PRIMARY KEY,
    document_type   TEXT NOT NULL,
    document_id     INTEGER NOT NULL,
    file_name       TEXT NOT NULL,
    storage_ref     TEXT NOT NULL,
    created_by      TEXT,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_attachment_document
    ON document_attachment (document_type, document_id, id);

-- Preserve the pre-v8.2 single-attachment references as attachment-library rows.
-- ON CONFLICT is expressed through NOT EXISTS because legacy installations do not
-- have a natural unique key for the old reference.
INSERT INTO document_attachment(document_type,document_id,file_name,storage_ref,created_by,created_at)
SELECT 'PURCHASE', p.id,
       regexp_replace(p.attachment_path, '^.*[\\/]', ''),
       p.attachment_path,
       COALESCE(p.created_by,''),
       COALESCE(p.created_at::text,CURRENT_TIMESTAMP::text)
FROM purchase_header p
WHERE COALESCE(p.attachment_path,'')<>''
  AND NOT EXISTS (
      SELECT 1 FROM document_attachment a
      WHERE a.document_type='PURCHASE' AND a.document_id=p.id AND a.storage_ref=p.attachment_path
  );

INSERT INTO document_attachment(document_type,document_id,file_name,storage_ref,created_by,created_at)
SELECT 'SALE', s.id,
       regexp_replace(s.attachment_path, '^.*[\\/]', ''),
       s.attachment_path,
       '',
       COALESCE(s.created_at::text,CURRENT_TIMESTAMP::text)
FROM sales_header s
WHERE COALESCE(s.attachment_path,'')<>''
  AND NOT EXISTS (
      SELECT 1 FROM document_attachment a
      WHERE a.document_type='SALE' AND a.document_id=s.id AND a.storage_ref=s.attachment_path
  );

INSERT INTO document_attachment(document_type,document_id,file_name,storage_ref,created_by,created_at)
SELECT 'QUOTATION', q.id,
       regexp_replace(q.attachment_path, '^.*[\\/]', ''),
       q.attachment_path,
       COALESCE(q.created_by,''),
       COALESCE(q.created_at::text,CURRENT_TIMESTAMP::text)
FROM quotation_header q
WHERE COALESCE(q.attachment_path,'')<>''
  AND NOT EXISTS (
      SELECT 1 FROM document_attachment a
      WHERE a.document_type='QUOTATION' AND a.document_id=q.id AND a.storage_ref=q.attachment_path
  );
