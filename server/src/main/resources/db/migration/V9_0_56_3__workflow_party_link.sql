-- Same-version 9.0.56 corrective migration: stable party linkage for Project Execution.
ALTER TABLE workflow_document ADD COLUMN IF NOT EXISTS party_id INTEGER REFERENCES party_master(id);
CREATE INDEX IF NOT EXISTS idx_workflow_document_party_id ON workflow_document(party_id,document_type,document_date DESC,id DESC);

-- Backfill only unambiguous exact-name matches within the expected party type.
UPDATE workflow_document w
SET party_id = match.id
FROM (
    SELECT UPPER(TRIM(name)) AS normalized_name, UPPER(TRIM(party_type)) AS party_type, MIN(id) AS id
    FROM party_master
    GROUP BY UPPER(TRIM(name)), UPPER(TRIM(party_type))
    HAVING COUNT(*) = 1
) match
WHERE w.party_id IS NULL
  AND UPPER(TRIM(COALESCE(w.party_name,''))) = match.normalized_name
  AND ((w.document_type IN ('PROJECT','SALES_ORDER','DISPATCH') AND match.party_type='CUSTOMER')
       OR (w.document_type IN ('PURCHASE_ORDER','GRN') AND match.party_type='SUPPLIER'));
