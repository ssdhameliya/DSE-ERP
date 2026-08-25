-- DSE ERP 9.0.0 Phase 5: optimistic edit protection for shared multi-user records.
-- Existing rows start at version 0; Hibernate increments the value on each managed update.
ALTER TABLE sales_header       ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_header    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_register   ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE party_master       ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE item_master        ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE lookup_master      ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE master_category    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE recon_supplier     ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_recon     ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_activity_entity_created
    ON activity_log(entity_type, entity_id, created_at DESC);
