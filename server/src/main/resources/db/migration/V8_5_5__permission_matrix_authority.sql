-- v8.5.5: repair role-permission persistence and make PURCHASE a first-class assignable role.
ALTER TABLE role_permission ADD COLUMN IF NOT EXISTS role_code TEXT;
ALTER TABLE role_permission ALTER COLUMN role_id DROP NOT NULL;
ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS role_permission_pkey;

DELETE FROM role_permission duplicate
USING role_permission keeper
WHERE duplicate.ctid > keeper.ctid
  AND UPPER(TRIM(COALESCE(duplicate.role_code,'')))=UPPER(TRIM(COALESCE(keeper.role_code,'')))
  AND duplicate.permission_id=keeper.permission_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_role_permission_role_code_permission
ON role_permission(UPPER(TRIM(role_code)),permission_id)
WHERE TRIM(COALESCE(role_code,''))<>'';

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT 'ROLE','ROL_PURCHASE','PURCHASE','Purchase operations access',40,1
WHERE NOT EXISTS (
    SELECT 1 FROM lookup_master lm
    JOIN master_category mc ON UPPER(TRIM(mc.category_name))=UPPER(TRIM(lm.lookup_type))
    WHERE UPPER(TRIM(mc.category_code))='ROLE' AND UPPER(TRIM(lm.lookup_value))='PURCHASE'
);

-- New Purchase role starts with Purchase/Supplier/Inventory view + normal Purchase create/edit/export capabilities.
INSERT INTO role_permission(role_code,permission_id,allowed)
SELECT 'PURCHASE',p.id,
       CASE WHEN p.module_name='PURCHASE' AND p.action_name IN ('VIEW','CREATE','EDIT','EXPORT') THEN 1
            WHEN p.module_name='SUPPLIERS' AND p.action_name IN ('VIEW','CREATE','EDIT') THEN 1
            WHEN p.module_name='INVENTORY' AND p.action_name='VIEW' THEN 1
            WHEN p.module_name='DASHBOARD' AND p.action_name='VIEW' THEN 1
            ELSE 0 END
FROM permissions p
WHERE p.active=1
ON CONFLICT (UPPER(TRIM(role_code)),permission_id) WHERE TRIM(COALESCE(role_code,''))<>'' DO NOTHING;
