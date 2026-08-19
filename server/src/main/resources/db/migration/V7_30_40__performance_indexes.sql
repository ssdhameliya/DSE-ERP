-- 7.30.40 performance support indexes. Safe/idempotent for existing databases.
CREATE INDEX IF NOT EXISTS idx_communication_log_channel_read
    ON communication_log(channel, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_read
    ON notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_reminder_register_status
    ON reminder_register(status);
CREATE INDEX IF NOT EXISTS idx_sales_line_sales_id
    ON sales_line(sales_id);
CREATE INDEX IF NOT EXISTS idx_purchase_line_purchase_id
    ON purchase_line(purchase_id);
CREATE INDEX IF NOT EXISTS idx_sales_charge_sales_id
    ON sales_charge(sales_id);
CREATE INDEX IF NOT EXISTS idx_payment_record_document
    ON payment_record(document_type, document_id);
