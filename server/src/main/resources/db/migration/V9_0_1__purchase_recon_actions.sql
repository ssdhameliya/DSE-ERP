INSERT INTO permissions(permission_key,module_name,action_name,description) VALUES
('RECON_SUPPLIER.DELETE','RECON_SUPPLIER','DELETE','Delete unused Recon Supplier master records'),
('PURCHASE_RECON.DELETE','PURCHASE_RECON','DELETE','Delete unlinked Purchase Recon records')
ON CONFLICT (permission_key) DO UPDATE SET description=EXCLUDED.description, active=1;

INSERT INTO role_permission(role_code,permission_id,allowed)
SELECT 'PURCHASE',p.id,1 FROM permissions p
WHERE p.permission_key IN ('RECON_SUPPLIER.DELETE','PURCHASE_RECON.DELETE')
ON CONFLICT (UPPER(TRIM(role_code)),permission_id) WHERE TRIM(COALESCE(role_code,''))<>'' DO NOTHING;
