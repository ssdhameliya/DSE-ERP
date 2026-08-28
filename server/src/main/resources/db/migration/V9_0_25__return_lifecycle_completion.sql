-- DSE ERP v9.0.25
-- Completes Return document/refund state separation introduced in v9.0.24.

UPDATE return_register
SET refund_status='WAITING APPROVAL', settlement_due_date=NULL
WHERE UPPER(COALESCE(status,'PENDING APPROVAL'))='PENDING APPROVAL';

UPDATE return_register
SET refund_status='N/A', settlement_due_date=NULL
WHERE UPPER(COALESCE(status,'')) IN ('REJECTED','CANCELLED','DELETED');

WITH totals AS (
    SELECT r.return_no,
           SUM(COALESCE(r.amount,0)) AS return_total,
           COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0))
                     FROM return_refund rr WHERE rr.return_no=r.return_no),0) AS refunded
    FROM return_register r
    WHERE UPPER(COALESCE(r.status,''))='APPROVED'
    GROUP BY r.return_no
)
UPDATE return_register r
SET refund_status=CASE
        WHEN t.refunded<=0.0001 THEN 'PENDING'
        WHEN t.refunded+0.0001>=t.return_total THEN 'PAID'
        ELSE 'PARTIAL'
    END
FROM totals t
WHERE r.return_no=t.return_no;
