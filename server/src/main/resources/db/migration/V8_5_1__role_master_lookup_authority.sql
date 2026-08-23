-- v8.5.1: make normal Master Data (master_category + lookup_master) the single role-definition source.
-- ROLE lookup_value is the runtime/security identity; lookup_code is only the generated generic Master ID.
-- The historical roles table/role_id values remain only for migration compatibility; active application code
-- no longer reads role definitions from them.

INSERT INTO master_category(category_code,category_name,description,display_order,is_active)
VALUES('ROLE','ROLE','Application roles used by login, user access and permission assignment',35,1)
ON CONFLICT (category_code) DO UPDATE SET
    category_name='ROLE',
    description=EXCLUDED.description,
    is_active=1;

INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
VALUES
 ('ROLE','ADMIN','Admin','Full application access',10,1),
 ('ROLE','MANAGER','Manager','Business management access',20,1),
 ('ROLE','SALES','Sales','Standard sales and operational access',30,1)
ON CONFLICT DO NOTHING;

-- Preserve any currently active custom legacy role as a Role Master lookup before removing runtime dependency.
INSERT INTO lookup_master(lookup_type,lookup_code,lookup_value,description,display_order,is_active)
SELECT 'ROLE', UPPER(TRIM(r.role_name)),
       INITCAP(REPLACE(LOWER(TRIM(r.role_name)),'_',' ')),
       COALESCE(NULLIF(TRIM(r.description),''),'Application role'),
       100 + ROW_NUMBER() OVER (ORDER BY r.id),
       COALESCE(r.active,1)
FROM roles r
WHERE TRIM(COALESCE(r.role_name,''))<>''
  AND UPPER(TRIM(r.role_name)) NOT IN ('SALE','USER','VIEWER','ADMINISTRATOR')
  AND NOT EXISTS (
      SELECT 1 FROM lookup_master lm
      WHERE UPPER(TRIM(lm.lookup_type))='ROLE'
        AND UPPER(TRIM(lm.lookup_value))=UPPER(TRIM(r.role_name))
  );

UPDATE users SET role='SALES' WHERE UPPER(TRIM(COALESCE(role,''))) IN ('SALE','USER','VIEWER');
UPDATE users SET role='ADMIN' WHERE UPPER(TRIM(COALESCE(role,'')))='ADMINISTRATOR';
UPDATE users SET role=UPPER(TRIM(role)) WHERE TRIM(COALESCE(role,''))<>'';

-- Permission assignments now key to the normalized ROLE lookup_value identity, not lookup_code or roles.id.
-- The historical column name role_code is retained for schema compatibility, but its value is derived from lookup_value.
ALTER TABLE role_permission ADD COLUMN IF NOT EXISTS role_code TEXT;
UPDATE role_permission rp
   SET role_code=UPPER(TRIM(r.role_name))
  FROM roles r
 WHERE rp.role_id=r.id
   AND TRIM(COALESCE(rp.role_code,''))='';
UPDATE role_permission SET role_code='SALES' WHERE UPPER(TRIM(COALESCE(role_code,''))) IN ('SALE','USER','VIEWER');
UPDATE role_permission SET role_code='ADMIN' WHERE UPPER(TRIM(COALESCE(role_code,'')))='ADMINISTRATOR';

ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS role_permission_role_id_fkey;
ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS role_permission_pkey;
ALTER TABLE role_permission ALTER COLUMN role_id DROP NOT NULL;

DELETE FROM role_permission duplicate
USING role_permission keeper
WHERE duplicate.ctid > keeper.ctid
  AND UPPER(TRIM(COALESCE(duplicate.role_code,'')))=UPPER(TRIM(COALESCE(keeper.role_code,'')))
  AND duplicate.permission_id=keeper.permission_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_role_permission_role_code_permission
ON role_permission(UPPER(TRIM(role_code)),permission_id)
WHERE TRIM(COALESCE(role_code,''))<>'';
CREATE INDEX IF NOT EXISTS idx_role_permission_role_code_allowed
ON role_permission(UPPER(TRIM(role_code)),allowed);

-- Keep legacy role_id populated where it already exists, but it is no longer authoritative.
-- New/updated users are validated against ROLE lookups and persist only the canonical users.role code.
UPDATE users SET role_id=NULL;

-- The three protected baseline identities must always remain available.
UPDATE lookup_master SET is_active=1
WHERE UPPER(TRIM(lookup_type))='ROLE' AND UPPER(TRIM(lookup_value)) IN ('ADMIN','MANAGER','SALES');
