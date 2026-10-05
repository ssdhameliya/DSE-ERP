-- V10_0_29_5__security_permissions_alignment.sql
-- Aligns User Access & Permission Matrix for v10.0.29 screens:
-- GST Compliance, General Ledger, Automation Center, and Purchase Recon

INSERT INTO permissions(permission_key, module_name, action_name, description) VALUES
('GST_COMPLIANCE.VIEW', 'GST_COMPLIANCE', 'VIEW', 'Open GST Compliance Center and tax filing summaries'),
('GST_COMPLIANCE.EXPORT', 'GST_COMPLIANCE', 'EXPORT', 'Export GSTR-1, GSTR-2B and GSTR-3B workbooks and returns'),
('GST_COMPLIANCE.RECONCILE', 'GST_COMPLIANCE', 'RECONCILE', 'Execute GSTR-2B automated reconciliation'),

('GENERAL_LEDGER.VIEW', 'GENERAL_LEDGER', 'VIEW', 'View Chart of Accounts, Trial Balance, P&L, Balance Sheet, and Journal Vouchers'),
('GENERAL_LEDGER.CREATE', 'GENERAL_LEDGER', 'CREATE', 'Create Journal Vouchers and add Accounts'),
('GENERAL_LEDGER.POST', 'GENERAL_LEDGER', 'POST', 'Post manual Journal Vouchers to General Ledger'),
('GENERAL_LEDGER.EXPORT', 'GENERAL_LEDGER', 'EXPORT', 'Export financial statements and ledger reports'),

('AUTOMATION.VIEW', 'AUTOMATION', 'VIEW', 'Open Automation Center and view rule statuses'),
('AUTOMATION.EDIT', 'AUTOMATION', 'EDIT', 'Configure automation rules, tolerances, and thresholds'),
('AUTOMATION.EXECUTE', 'AUTOMATION', 'EXECUTE', 'Manually trigger 3-way match, stock replenishment, and scheduled rules')
ON CONFLICT (permission_key) DO UPDATE SET description = EXCLUDED.description, active = 1;

-- Grant all to ADMIN role
INSERT INTO role_permission(role_code, permission_id, allowed)
SELECT 'ADMIN', p.id, 1
FROM permissions p
WHERE p.module_name IN ('GST_COMPLIANCE', 'GENERAL_LEDGER', 'AUTOMATION', 'PURCHASE_RECON', 'RECON_SUPPLIER', 'AUDIT')
ON CONFLICT (UPPER(TRIM(role_code)), permission_id) WHERE TRIM(COALESCE(role_code, '')) <> ''
DO UPDATE SET allowed = 1;

-- Grant operational permissions to MANAGER role
INSERT INTO role_permission(role_code, permission_id, allowed)
SELECT 'MANAGER', p.id, 1
FROM permissions p
WHERE p.permission_key IN (
    'GST_COMPLIANCE.VIEW', 'GST_COMPLIANCE.EXPORT', 'GST_COMPLIANCE.RECONCILE',
    'GENERAL_LEDGER.VIEW', 'GENERAL_LEDGER.CREATE', 'GENERAL_LEDGER.EXPORT',
    'PURCHASE_RECON.VIEW', 'PURCHASE_RECON.CREATE', 'PURCHASE_RECON.EDIT', 'PURCHASE_RECON.IMPORT', 'PURCHASE_RECON.MATCH',
    'RECON_SUPPLIER.VIEW', 'RECON_SUPPLIER.CREATE', 'RECON_SUPPLIER.EDIT',
    'AUTOMATION.VIEW'
)
ON CONFLICT (UPPER(TRIM(role_code)), permission_id) WHERE TRIM(COALESCE(role_code, '')) <> ''
DO UPDATE SET allowed = 1;
