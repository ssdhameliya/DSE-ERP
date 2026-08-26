-- v9.0.11 Finance runtime repair.
-- Idempotently restores the columns/functions used by Bank Entry and Expense Entry
-- for installations upgraded across several historical schema generations.

ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS account_name TEXT;
ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS bill_path TEXT;
ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS reconciled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

UPDATE finance_register SET row_version=0 WHERE row_version IS NULL;
UPDATE finance_register SET reconciled=0 WHERE reconciled IS NULL;

ALTER TABLE bank_reconciliation_allocation ADD COLUMN IF NOT EXISTS finance_entry_id INTEGER;
ALTER TABLE bank_reconciliation_allocation ADD COLUMN IF NOT EXISTS rounding_adjustment NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE bank_reconciliation_allocation ADD COLUMN IF NOT EXISTS reversed_at VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_bank_recon_allocation_finance_active
    ON bank_reconciliation_allocation(finance_entry_id)
    WHERE finance_entry_id IS NOT NULL AND reversed_at IS NULL;

-- Recreate the exact TEXT signature even when an older/partial database already
-- contains another overload with the same name.
CREATE OR REPLACE FUNCTION dse_safe_date(value TEXT) RETURNS DATE
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
 v TEXT := BTRIM(COALESCE(value,''));
 parsed DATE;
BEGIN
 IF v ~ '^\d{4}-\d{2}-\d{2}' THEN
  parsed := TO_DATE(LEFT(v,10),'YYYY-MM-DD');
  IF TO_CHAR(parsed,'YYYY-MM-DD')=LEFT(v,10) THEN RETURN parsed; END IF;
 ELSIF v ~ '^\d{2}/\d{2}/\d{4}' THEN
  parsed := TO_DATE(LEFT(v,10),'DD/MM/YYYY');
  IF TO_CHAR(parsed,'DD/MM/YYYY')=LEFT(v,10) THEN RETURN parsed; END IF;
 ELSIF v ~ '^\d{2}-\d{2}-\d{4}' THEN
  parsed := TO_DATE(LEFT(v,10),'DD-MM-YYYY');
  IF TO_CHAR(parsed,'DD-MM-YYYY')=LEFT(v,10) THEN RETURN parsed; END IF;
 END IF;
 RETURN NULL;
EXCEPTION WHEN OTHERS THEN
 RETURN NULL;
END $$;
