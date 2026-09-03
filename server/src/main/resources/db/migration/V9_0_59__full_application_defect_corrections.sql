-- DSE ERP 9.0.59 corrective migration.
-- Keep existing business data; only reconcile rows created by defects fixed in this release.

-- Quotation follow-up edits historically left earlier OPEN/SNOOZED reminders behind.
-- Cancel reminders that no longer match the quotation's current follow-up date (or where follow-up was cleared).
UPDATE reminder_register r
SET status = 'CANCELLED',
    updated_at = COALESCE(NULLIF(r.updated_at, ''), CURRENT_TIMESTAMP::text)
FROM quotation_header q
WHERE r.reference_no = q.quotation_no
  AND r.title LIKE 'Quotation follow-up:%'
  AND UPPER(COALESCE(r.status, '')) IN ('OPEN', 'SNOOZED')
  AND (NULLIF(q.follow_up_date, '') IS NULL OR COALESCE(r.due_date, '') <> q.follow_up_date);

-- If duplicate active reminders have the same current date, keep only the newest one.
WITH ranked AS (
    SELECT r.id,
           ROW_NUMBER() OVER (
               PARTITION BY r.reference_no
               ORDER BY COALESCE(NULLIF(r.updated_at, ''), r.created_at, '') DESC, r.id DESC
           ) AS rn
    FROM reminder_register r
    JOIN quotation_header q ON q.quotation_no = r.reference_no
    WHERE r.title LIKE 'Quotation follow-up:%'
      AND UPPER(COALESCE(r.status, '')) IN ('OPEN', 'SNOOZED')
      AND NULLIF(q.follow_up_date, '') IS NOT NULL
      AND COALESCE(r.due_date, '') = q.follow_up_date
)
UPDATE reminder_register r
SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP::text
FROM ranked x
WHERE r.id = x.id AND x.rn > 1;

-- Enforce one active quotation follow-up reminder per quotation going forward.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reminder_active_quotation_followup
    ON reminder_register(reference_no)
    WHERE title LIKE 'Quotation follow-up:%'
      AND UPPER(COALESCE(status, '')) IN ('OPEN', 'SNOOZED');

-- Older quotation conversions could create a 30-day due date while leaving Payment Terms blank.
UPDATE sales_header h
SET payment_terms = '30 Days'
FROM quotation_header q
WHERE q.converted_invoice_no = h.invoice_no
  AND COALESCE(BTRIM(h.payment_terms), '') = '';
