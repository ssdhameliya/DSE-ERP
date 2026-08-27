from pathlib import Path
import hashlib
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8', errors='replace')

def req(cond, msg):
    if not cond:
        print(f'FAIL: {msg}', file=sys.stderr)
        raise SystemExit(1)

ops = text('server/src/main/java/org/example/server/operations/BusinessOperationsService.java')
security = text('server/src/main/java/org/example/server/security/SecurityConfig.java')
support = text('server/src/main/java/org/example/server/support/SupportService.java')
email = text('server/src/main/java/org/example/server/authority/BusinessEmailController.java')
pdf = text('server/src/main/java/org/example/server/authority/PdfStudioTemplateController.java')
insights_c = text('server/src/main/java/org/example/server/insights/InsightsController.java')
insights_s = text('server/src/main/java/org/example/server/insights/InsightsService.java')
master = text('server/src/main/java/org/example/server/master/MasterDataService.java')
admin = text('server/src/main/java/org/example/server/admin/AdminService.java')
quote = text('server/src/main/java/org/example/server/quotation/QuotationService.java')
recon = text('server/src/main/java/org/example/server/reconciliation/BankReconciliationService.java')
importer = text('desktop/src/main/java/org/example/service/ImportService.java')
sales_ui = text('desktop/src/main/java/org/example/controller/SalesController.java')
ref_rules = text('shared/src/main/java/org/example/shared/ReferenceFormatRules.java')
migration = text('server/src/main/resources/db/migration/V9_0_9__corrective_integrity_hardening.sql')
migration_runner = text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
purchase_header = text('server/src/main/java/org/example/server/persistence/entity/PurchaseHeaderEntity.java')
purchase_line = text('server/src/main/java/org/example/server/persistence/entity/PurchaseLineEntity.java')
purchase_recon = text('server/src/main/java/org/example/server/recon/PurchaseReconService.java')
runtime_bootstrap = text('desktop/src/main/java/org/example/api/runtime/RuntimeBootstrapper.java')

# 1. Server compile helpers: every formerly missing helper has a declaration.
helpers = [
    'saleQuantityTotals', 'saleChargeSummaries', 'saleSummaryDto', 'saleDto',
    'recordedPurchasePayments', 'purchaseQuantityTotals', 'purchaseChargeSummaries',
    'purchaseSummaryDto', 'purchaseDto'
]
for name in helpers:
    req(re.search(r'\bprivate\s+[^;{}]+\b' + re.escape(name) + r'\s*\(', ops) is not None,
        f'BusinessOperationsService helper must be implemented: {name}')

# 2. Bank Statement/reconciliation authorization must cover the actual controller path.
req('"/api/bank-statements/**", "/api/reconciliation/**"' in security and '"BANK_EXPENSE.RECONCILE"' in security,
    'bank-statement reconciliation routes must require BANK_EXPENSE.RECONCILE')

# 3. Business email must be permission-gated at both route and controller boundary.
req('HttpMethod.POST, "/api/authority/email"' in security and '"COMMUNICATION.CREATE"' in security,
    'business email POST route must require COMMUNICATION.CREATE')
req('CurrentUser.requirePermission("COMMUNICATION.CREATE"' in email,
    'business email controller must enforce COMMUNICATION.CREATE')

# 4. PDF Studio shared template mutation requires template-management permission.
req('HttpMethod.PUT, "/api/pdf-studio/templates/**"' in security and
    'HttpMethod.DELETE, "/api/pdf-studio/templates/**"' in security and
    security.count('DOCUMENT_STUDIO.MANAGE_TEMPLATES') >= 2,
    'PDF Studio PUT/DELETE routes must require DOCUMENT_STUDIO.MANAGE_TEMPLATES')
req(pdf.count('CurrentUser.requirePermission("DOCUMENT_STUDIO.MANAGE_TEMPLATES"') >= 2,
    'PDF Studio controller must enforce template-management permission')

# 5. Reminder/notification/communication endpoints must enforce their module permissions.
for permission in ['REMINDERS.VIEW','REMINDERS.CREATE','REMINDERS.EDIT','REMINDERS.DELETE',
                   'COMMUNICATION.VIEW','COMMUNICATION.CREATE','COMMUNICATION.EDIT','COMMUNICATION.DELETE']:
    req(permission in insights_c or permission in insights_s or permission in support,
        f'missing communication/reminder permission enforcement: {permission}')
req('REMINDERS.SNOOZE' in insights_s and 'REMINDERS.COMPLETE' in insights_s,
    'reminder status transitions must enforce SNOOZE/COMPLETE permissions')

# 6. Duplicate Sale must require SALES.CREATE and delegate to canonical operation path.
req('CurrentUser.requirePermission("SALES.CREATE","DuplicateSale")' in support.replace(' ',''),
    'Support duplicate Sale must require SALES.CREATE')
req('operations.duplicateSale(id)' in support,
    'Support duplicate Sale must delegate to the canonical business operation')

# 7. Purchase history must snapshot supplier/item descriptive/cost data.
for field in ['supplierNameSnapshot','supplierEmailSnapshot','supplierPhoneSnapshot','supplierGstinSnapshot','supplierAddressSnapshot']:
    req(field.lower() in purchase_header.lower(), f'purchase header snapshot missing: {field}')
for field in ['itemDescriptionSnapshot','hsnSnapshot','unitSnapshot','itemRemarksSnapshot','unitCostSnapshot']:
    req(field.lower() in purchase_line.lower(), f'purchase line snapshot missing: {field}')
for col in ['supplier_name_snapshot','supplier_email_snapshot','supplier_phone_snapshot','supplier_gstin_snapshot','supplier_address_snapshot',
            'item_description_snapshot','hsn_snapshot','unit_snapshot','item_remarks_snapshot','unit_cost_snapshot']:
    req(col in migration, f'v9.0.9 migration missing purchase snapshot column/backfill: {col}')
req('snapshotPurchaseParty(h)' in ops and 'setItemDescriptionSnapshot' in ops and 'setUnitCostSnapshot' in ops,
    'purchase save/update path must populate snapshots')

# 8. Item Master reads require INVENTORY.VIEW.
req('HttpMethod.GET, "/api/master/items/**"' in security and '"INVENTORY.VIEW"' in security,
    'Item Master GET route must require INVENTORY.VIEW')
req(master.count('CurrentUser.requirePermission("INVENTORY.VIEW"') >= 2,
    'Item list and search service methods must require INVENTORY.VIEW')

# 9. Legacy textual dates use the safe parser in affected stock/quotation/report paths.
req('CREATE OR REPLACE FUNCTION dse_safe_date' in migration, 'safe legacy-date parser migration missing')
req('V9_0_9__corrective_integrity_hardening' in migration_runner and 'dollarQuote' in migration_runner,
    'runtime migration runner must register V9_0_9 and support dollar-quoted SQL blocks')
req(ops.count('dse_safe_date(') >= 4, 'stock/operation date queries must use dse_safe_date')
req('dse_safe_date(' in quote and 'dse_safe_date(' in support,
    'quotation/report queries must use dse_safe_date')

# 10. WhatsApp status must not swallow persistence failures.
req('markWhatsappSent' in support and 'int updated=jdbc.update' in support and 'if(updated!=1)throw' in support.replace(' ',''),
    'WhatsApp status update must propagate DB errors and reject zero-row updates')
whatsapp_line = next((line for line in support.splitlines() if 'markWhatsappSent' in line), '')
req('catch' not in whatsapp_line, 'WhatsApp status update must not swallow database exceptions')

# 11. Purchase import rerun identity uses external reference as well as internal invoice number.
req('doc.getReferenceNo()' in importer and 'doc.getInvoiceNo()' in importer and
    importer.count('addDocumentIdentity(existingDocument') >= 4,
    'Purchase import must compare external reference and internal invoice identities')
req('if (sales) requireReference(referenceFormats, "REF_SALES", invoice' in importer,
    'Sales import invoice format validation must remain enforced')
# Purchase external supplier reference must not be forced through REF_PURCHASE.
req('requireReference(referenceFormats, sales ? "REF_SALES" : "REF_PURCHASE", invoice' not in importer,
    'Purchase external reference must not be validated as internal REF_PURCHASE')

# 12. Reference format validation is strict: exactly one X sequence group.
req('requireValidFormat' in ref_rules and 'Pattern.compile("X{2,}")' in ref_rules and
    'if (!matcher.find())' in ref_rules and 'if (matcher.find())' in ref_rules,
    'ReferenceFormatRules must require exactly one sequence group')
req('ReferenceFormatRules.requireValidFormat(d.lookupValue())' in master,
    'Master Data must reject invalid reference formats before persistence')

# 13. Duplicate Sale must clear original customer Order/PO number on desktop and server.
req('prepareDuplicate()' in sales_ui and 'txtOrderNo.clear()' in sales_ui,
    'desktop duplicate Sale must clear customer Order/PO number')
# Canonical duplicate request contains a null Order No before saveSale.
dup = re.search(r'OperationDtos\.SaleDto\s+duplicateSale\s*\(int id\)\s*\{(.*?)\n \}\n @Transactional public void deleteSale', ops, re.S)
req(dup is not None and 'd.transportNote(),null,d.gstin()' in dup.group(1).replace(' ',''),
    'server duplicate Sale must not carry the original unique Order/PO number')

# 14. Server duplicate Sale must rebuild via canonical save rather than copy stale total/header fragments.
req(dup is not None and 'saleDto(source,true)' in dup.group(1) and 'saveSale(request)' in dup.group(1) and
    'd.discountAmount()' in dup.group(1) and 'd.charges()' in dup.group(1) and 'd.lines()' in dup.group(1),
    'server duplicate Sale must rebuild through canonical saveSale from complete source DTO')

# 15. Finance entries linked to active allocations cannot be edited/deleted, and DB has an FK.
req(ops.count('findByFinanceEntryIdAndReversedAtIsNull') >= 2 and
    'Reconciled finance entries must be reversed from Bank Statement before editing.' in ops and
    'Reconciled finance entries must be reversed from Bank Statement before deletion.' in ops,
    'Finance edit/delete must block active bank allocations')
req('e.setReconciled(reconciled)' in ops, 'Finance update must preserve server-owned reconciled state')
req('FOREIGN KEY(finance_entry_id) REFERENCES finance_register(id) ON DELETE RESTRICT' in migration,
    'bank allocation must have Finance Register FK integrity')
req('a.setFinanceEntryId(null)' in recon, 'reconciliation reversal must detach allocation before deleting finance entry')

# 16. User administration must protect self role and last active administrator.
req('ensureActiveAdministratorRemains' in admin and admin.count('ensureActiveAdministratorRemains') >= 3,
    'user admin must enforce last-active-admin protection across destructive authority changes')
req('assignedRole' in admin and ('own role' in admin.lower() or 'your own role' in admin.lower()),
    'administrator must not be able to change their own role')

# 17. Party type and name validation must reject invalid/blank master records.
req('Set.of("CUSTOMER", "SUPPLIER")' in master and master.count('Party type must be CUSTOMER or SUPPLIER') >= 2,
    'party type must be restricted to CUSTOMER/SUPPLIER')
req('Party name is required' in master, 'party name must be mandatory')

# 18. Category rename/upsert must migrate lookup rows to the new type name.
req('previousName' in master and 'findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(previousName)' in master and
    'value.setLookupType(nextName)' in master,
    'Master category upsert rename must migrate existing lookup values')

# 19. Quotation duplicate/convert must use an internal line loader without an extra VIEW permission gate.
req('private List<QuotationDtos.LineDto> loadLines' in quote,
    'Quotation service must provide internal line loader for authorized actions')
convert = re.search(r'convert\s*\([^)]*\).*?\n\s*\}', quote, re.S)
duplicate = re.search(r'duplicate\s*\([^)]*\).*?\n\s*\}', quote, re.S)
req('loadLines(' in quote and quote.count('loadLines(') >= 3,
    'Quotation action paths must use internal loadLines')

# 20. Dashboard cash position includes manual bank deposits and withdrawals.
req('BANK DEPOSIT' in insights_s and 'BANK WITHDRAWAL' in insights_s and
    'bankDeposits' in insights_s and 'bankWithdrawals' in insights_s,
    'Dashboard cash position must include manual bank deposit/withdrawal finance entries')


# v9.0.12 compile/cache correction: Purchase Recon import has no Other Adjustment column,
# and source-mode bootstrap must not reuse a cached server JAR from another app version.
req('row.otherAdjustment()' not in purchase_recon and 'round2(0d)' in purchase_recon,
    'Purchase Recon import signature must use the defined zero Other Adjustment rather than a nonexistent ImportRow accessor')
req('isExpectedDevelopmentServerJar(cached)' in runtime_bootstrap and
    'Implementation-Version' in runtime_bootstrap and
    'RuntimeContract.APP_VERSION.equals(version)' in runtime_bootstrap,
    'IntelliJ server cache must reject cached JARs from a different DSE ERP version')

# Release identity must be one exact 9.0.12 contract across active artifacts.
root_pom = text('pom.xml')
server_pom = text('server/pom.xml')
desktop_pom = text('desktop/pom.xml')
shared_pom = text('shared/pom.xml')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props = text('server/src/main/resources/application.properties')
app_version = text('desktop/src/main/resources/app-version.properties')
update = text('desktop/src/main/java/org/example/update/UpdateService.java')
runtime_manifest = text('runtime/runtime-manifest.properties') if (ROOT/'runtime/runtime-manifest.properties').exists() else ''
run_bat = text('Run DSE ERP.bat')
build_bat = text('Build Production Windows.bat')
postgres_bat = text('scripts/start-postgresql.cmd')
safe_rollback = text('desktop/src/main/resources/fxml/pages/SafeRollback.fxml')

req('<version>9.0.22</version>' in root_pom and '<dse.phase>9.0.22</dse.phase>' in root_pom,
    'root Maven release identity must be 9.0.18')
for name,pom in [('server',server_pom),('desktop',desktop_pom),('shared',shared_pom)]:
    req('<version>9.0.22</version>' in pom, f'{name} parent version must be 9.0.18')
req('APP_VERSION = "9.0.22"' in runtime and 'BUILD_REVISION = "9.0.22"' in runtime,
    'shared runtime identity must be 9.0.18')
req('dse.app.version=9.0.22' in props and 'dse.build.revision=9.0.22' in props,
    'server runtime identity must be 9.0.18')
req('version=9.0.22' in app_version and 'DEFAULT_VERSION="9.0.22"' in update,
    'desktop resource/updater identity must be 9.0.18')
if runtime_manifest:
    req('runtime.phase=9.0.22' in runtime_manifest, 'bundled runtime phase must be 9.0.18')
req('DSE ERP 9.0.22 - DEVELOPMENT / INTELLIJ ONLY' in run_bat,
    'IntelliJ launcher banner must be 9.0.18')
req('DSE ERP 9.0.22 - PRODUCTION WINDOWS BUILD' in build_bat,
    'production build banner must be 9.0.18')
req('DSE ERP 9.0.22 uses application-managed PostgreSQL.' in postgres_bat,
    'PostgreSQL launcher banner must be 9.0.18')
req('fx:id="lblCurrentVersion" text="9.0.22"' in safe_rollback,
    'Safe Rollback current-version fallback must be 9.0.18')

# Locked production document-generation boundary: unchanged from corrected v9.0.8.
protected = {
    'desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java': '5d84c57c22299bfedcc969512b33f2a8cd0371455918ef82f71037827ee2686c',
    'desktop/src/main/java/org/example/service/InvoicePdfService.java': '349e9f1f863c122826cad2560091f2dac87cdb9b2bcb0a42f5825fd312feb778',
    'desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java': '27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082',
}
for rel, expected in protected.items():
    actual = hashlib.sha256((ROOT/rel).read_bytes()).hexdigest()
    req(actual == expected, f'locked production PDF/Sales generation file changed: {rel}')

print('PASS: DSE ERP 9.0.18 runtime with consolidated 20-defect corrective contract')
