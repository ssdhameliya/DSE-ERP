-- V10_0_28__defect_hardening.sql
-- Indian GST classification and defect hardening

ALTER TABLE item_master ADD COLUMN IF NOT EXISTS gst_supply_type VARCHAR(20) DEFAULT 'TAXABLE';
