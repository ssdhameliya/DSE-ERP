-- v7.3.17 Purchase inventory lifecycle guard.
-- Existing Purchase rows were created by releases that always posted inventory, including DRAFT.
-- Preserve that fact for existing active rows, while new DRAFT rows can explicitly remain unposted.
ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS inventory_posted BOOLEAN;
UPDATE purchase_header
SET inventory_posted = CASE
    WHEN UPPER(COALESCE(document_status,'')) IN ('DELETED','CANCELLED') THEN FALSE
    ELSE TRUE
END
WHERE inventory_posted IS NULL;
ALTER TABLE purchase_header ALTER COLUMN inventory_posted SET DEFAULT FALSE;
ALTER TABLE purchase_header ALTER COLUMN inventory_posted SET NOT NULL;
