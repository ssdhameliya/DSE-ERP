-- DSE ERP 9.0.47 - Financial Integrity & Multi-User Hardening

ALTER TABLE quotation_header
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS role_permission_revision (
    role_code VARCHAR(120) PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at VARCHAR(40) NOT NULL DEFAULT CURRENT_TIMESTAMP::text
);

INSERT INTO role_permission_revision(role_code,row_version)
SELECT DISTINCT UPPER(TRIM(role_code)),0
FROM role_permission
WHERE COALESCE(TRIM(role_code),'')<>''
ON CONFLICT(role_code) DO NOTHING;

-- Keep inventory cost precision aligned with the Java 9.0.47 contract.
ALTER TABLE inventory_cost_state
    ALTER COLUMN average_unit_cost TYPE NUMERIC(18,4) USING ROUND(COALESCE(average_unit_cost,0)::numeric,4);

ALTER TABLE inventory_cost_ledger
    ALTER COLUMN unit_cost TYPE NUMERIC(18,4) USING ROUND(COALESCE(unit_cost,0)::numeric,4);

ALTER TABLE sales_line
    ALTER COLUMN unit_cost_snapshot TYPE NUMERIC(18,4) USING ROUND(COALESCE(unit_cost_snapshot,0)::numeric,4);

ALTER TABLE purchase_line
    ALTER COLUMN unit_cost_snapshot TYPE NUMERIC(18,4) USING ROUND(COALESCE(unit_cost_snapshot,0)::numeric,4);
