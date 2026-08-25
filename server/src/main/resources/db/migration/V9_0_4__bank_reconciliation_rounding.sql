-- DSE ERP 9.0.4: explicit bank reconciliation round-off and shared policy.
ALTER TABLE bank_reconciliation_allocation
    ADD COLUMN IF NOT EXISTS rounding_adjustment NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE return_refund
    ADD COLUMN IF NOT EXISTS rounding_adjustment NUMERIC(18,2) NOT NULL DEFAULT 0;

INSERT INTO application_setting(setting_key,setting_value)
VALUES('payment.bankMatchRoundingTolerance','1.00')
ON CONFLICT (setting_key) DO NOTHING;
