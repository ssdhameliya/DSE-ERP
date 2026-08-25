-- v9.0.3: repeatable Purchase Recon imports and source-sheet traceability.
-- Fingerprints remain audit evidence, but row-level business keys decide create/update/skip.
ALTER TABLE purchase_recon_import_batch
    DROP CONSTRAINT IF EXISTS purchase_recon_import_batch_source_fingerprint_key;
DROP INDEX IF EXISTS purchase_recon_import_batch_source_fingerprint_key;

CREATE INDEX IF NOT EXISTS idx_purchase_recon_import_fingerprint
    ON purchase_recon_import_batch (source_fingerprint);

ALTER TABLE purchase_recon
    ADD COLUMN IF NOT EXISTS source_sheet TEXT;

CREATE INDEX IF NOT EXISTS idx_purchase_recon_import_source
    ON purchase_recon (import_batch_id, source_sheet, source_row);
