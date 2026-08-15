-- 7.3.2 UI/security catalog alignment. Existing permission keys stay active because
-- desktop/server authorization still consumes them; this migration adds current modules
-- and workflow-specific capabilities without deleting customer role assignments.
INSERT INTO permissions(permission_key,module_name,action_name,description) VALUES
('BANK_EXPENSE.VIEW','BANK_EXPENSE','VIEW','Open Bank Entry, Expense Entry and Bank Statement'),
('BANK_EXPENSE.CREATE','BANK_EXPENSE','CREATE','Create bank and expense transactions'),
('BANK_EXPENSE.EDIT','BANK_EXPENSE','EDIT','Edit bank and expense transactions'),
('BANK_EXPENSE.DELETE','BANK_EXPENSE','DELETE','Delete eligible bank and expense transactions'),
('BANK_EXPENSE.RECONCILE','BANK_EXPENSE','RECONCILE','Match and reconcile bank statement transactions'),
('BANK_EXPENSE.EXPORT','BANK_EXPENSE','EXPORT','Export bank and expense data'),
('DOCUMENT_STUDIO.VIEW','DOCUMENT_STUDIO','VIEW','Open Document Studio'),
('DOCUMENT_STUDIO.CREATE','DOCUMENT_STUDIO','CREATE','Create or import documents and templates'),
('DOCUMENT_STUDIO.EDIT','DOCUMENT_STUDIO','EDIT','Edit Document Studio content'),
('DOCUMENT_STUDIO.EXPORT_PDF','DOCUMENT_STUDIO','EXPORT_PDF','Export Document Studio PDF output'),
('DOCUMENT_STUDIO.MANAGE_TEMPLATES','DOCUMENT_STUDIO','MANAGE_TEMPLATES','Manage ERP and general document templates'),
('SAFE_ROLLBACK.VIEW','SAFE_ROLLBACK','VIEW','Open Safe Rollback'),
('SAFE_ROLLBACK.PREPARE','SAFE_ROLLBACK','PREPARE','Prepare a rollback recovery point'),
('SAFE_ROLLBACK.EXECUTE','SAFE_ROLLBACK','EXECUTE','Execute a validated application rollback'),
('APPLICATION_UPDATES.VIEW','APPLICATION_UPDATES','VIEW','View application update status'),
('APPLICATION_UPDATES.CHECK','APPLICATION_UPDATES','CHECK','Check for application releases'),
('APPLICATION_UPDATES.INSTALL','APPLICATION_UPDATES','INSTALL','Install an approved application update'),
('REMINDERS.COMPLETE','REMINDERS','COMPLETE','Mark reminders complete or reopen them'),
('REMINDERS.SNOOZE','REMINDERS','SNOOZE','Snooze reminders to a future date'),
('COMMUNICATION.RESEND','COMMUNICATION','RESEND','Re-send supported communication records'),
('USERS.MANAGE_ROLES','USERS','MANAGE_ROLES','Edit protected role descriptions'),
('USERS.MANAGE_PERMISSIONS','USERS','MANAGE_PERMISSIONS','Change role permission assignments')
ON CONFLICT (permission_key) DO UPDATE SET description=EXCLUDED.description, active=1;

INSERT INTO role_permission(role_id,permission_id,allowed)
SELECT r.id,p.id,CASE WHEN r.role_name='ADMIN' THEN 1 ELSE 0 END
FROM roles r CROSS JOIN permissions p
WHERE p.permission_key IN (
'BANK_EXPENSE.VIEW','BANK_EXPENSE.CREATE','BANK_EXPENSE.EDIT','BANK_EXPENSE.DELETE','BANK_EXPENSE.RECONCILE','BANK_EXPENSE.EXPORT',
'DOCUMENT_STUDIO.VIEW','DOCUMENT_STUDIO.CREATE','DOCUMENT_STUDIO.EDIT','DOCUMENT_STUDIO.EXPORT_PDF','DOCUMENT_STUDIO.MANAGE_TEMPLATES',
'SAFE_ROLLBACK.VIEW','SAFE_ROLLBACK.PREPARE','SAFE_ROLLBACK.EXECUTE',
'APPLICATION_UPDATES.VIEW','APPLICATION_UPDATES.CHECK','APPLICATION_UPDATES.INSTALL',
'REMINDERS.COMPLETE','REMINDERS.SNOOZE','COMMUNICATION.RESEND','USERS.MANAGE_ROLES','USERS.MANAGE_PERMISSIONS')
ON CONFLICT (role_id,permission_id) DO NOTHING;
