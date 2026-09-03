#!/usr/bin/env python3
"""DSE ERP financial integrity and multi-user hardening contract."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FAIL = []

def text(rel):
    return (ROOT / rel).read_text(encoding="utf-8", errors="ignore")

def need(ok, message):
    if not ok:
        FAIL.append(message)

calc = text("shared/src/main/java/org/example/shared/DocumentCalculationEngine.java")
invoice_calc = text("desktop/src/main/java/org/example/invoice/calculation/InvoiceTaxCalculator.java")
ops = text("server/src/main/java/org/example/server/operations/BusinessOperationsService.java")
payments = text("server/src/main/java/org/example/server/support/PaymentIntegrityService.java")
returns = text("server/src/main/java/org/example/server/returns/ReturnService.java")
quotation_dto = text("server/src/main/java/org/example/server/quotation/QuotationDtos.java")
quotation = text("server/src/main/java/org/example/server/quotation/QuotationService.java")
quotation_client = text("desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java")
quotation_editor = text("desktop/src/main/java/org/example/controller/QuotationEditorController.java")
admin_dto = text("server/src/main/java/org/example/server/admin/AdminDtos.java")
admin = text("server/src/main/java/org/example/server/admin/AdminService.java")
admin_client = text("desktop/src/main/java/org/example/api/admin/AdminApiClient.java")
permission = text("desktop/src/main/java/org/example/controller/PermissionMatrixController.java")
user_access = text("desktop/src/main/java/org/example/controller/UserAccessController.java")
resource_service = text("server/src/main/java/org/example/server/authority/ServerResourceService.java")
resource_controller = text("server/src/main/java/org/example/server/authority/ServerResourceController.java")
resource_client = text("desktop/src/main/java/org/example/api/authority/ServerResourceClient.java")
remote_mirror = text("desktop/src/main/java/org/example/documentstudio/service/RemoteTemplateMirror.java")
pdf_remote = text("desktop/src/main/java/org/example/documentstudio/service/PdfStudioRemoteStore.java")
renderer = text("desktop/src/main/java/org/example/util/ProfessionalDocumentRenderer.java")
return_editor = text("desktop/src/main/java/org/example/service/ReturnEditorService.java")
template_data = text("desktop/src/main/java/org/example/documentstudio/service/TemplateDataFactory.java")
bank = text("server/src/main/java/org/example/server/reconciliation/BankReconciliationService.java")
recon = text("server/src/main/java/org/example/server/recon/PurchaseReconService.java")
migration = text("server/src/main/resources/db/migration/V9_0_47__financial_multi_user_hardening.sql")
runner = text("server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java")

# One numeric authority: money/rates 2dp, quantity/cost 4dp.
need(("scaled(value, 2" in calc or "setScale(2, RoundingMode.HALF_UP)" in calc) and "RoundingMode.HALF_UP" in calc, "canonical 2dp HALF_UP money contract missing")
need(("scaled(value, 4" in calc or "setScale(4, RoundingMode.HALF_UP)" in calc) and "RoundingMode.HALF_UP" in calc, "canonical 4dp quantity/unit-cost contract missing")
need("public static double quantity(double value)" in calc, "canonical quantity normalizer missing")
need("public static double unitCost(double value)" in calc, "canonical unit-cost normalizer missing")
need(("quantity(input.quantity())" in calc and "money(finiteNonNegative(input.rate()))" in calc) or ("quantityDecimal(input.quantity())" in calc and "moneyDecimal(finiteNonNegative(input.rate()))" in calc), "line calculation must normalize quantity and rate before calculation")
need("DocumentCalculationEngine.totals" in invoice_calc, "Invoice/PDF calculation must delegate to canonical DocumentCalculationEngine")
need("private static BigDecimal cost(double value)" in ops and "setScale(4,RoundingMode.HALF_UP)" in ops and "DocumentCalculationEngine.unitCost" in ops, "inventory unit-cost precision is not 4dp")
need("return DocumentCalculationEngine.money(v);" in bank and "DocumentCalculationEngine.money(v).doubleValue()" not in bank, "bank reconciliation must delegate directly to canonical money rounding")
need("return DocumentCalculationEngine.money(v);" in recon and "DocumentCalculationEngine.money(v).doubleValue()" not in recon, "purchase reconciliation must delegate directly to canonical money rounding")
need("AmountInWordsConverter.indianRupees(amount)" in renderer, "amount-in-words must preserve paise")
need("item.discountPercent()" in return_editor and "DocumentCalculationEngine.line" in return_editor, "return editor preview does not preserve original line discount")
need("returnLineFinancials" in template_data and "line.amount()" in template_data and '"totals.discountAmount", money(discount)' in template_data, "return PDF/XLSX breakdown does not use the server-authoritative discounted return amount")

# Settlement authority includes reconciliation rounding adjustments after later payment edits.
need("private BigDecimal effectivePaid" in payments, "authoritative effective-paid calculation missing")
need("rounding_adjustment" in payments and "a.reversed_at IS NULL" in payments, "effective-paid calculation does not include active bank roundoff")
need("effectivePaid(existing.type, existing.documentId, paymentId)" in payments, "payment edit does not exclude the edited payment from authoritative paid total")
need("BigDecimal paid = effectivePaid(existing.type, existing.documentId, null)" in payments, "payment edit does not rebuild final paid total from authoritative settlement records")

# Partial returns assign the exact remaining paise when a source line closes and serialize creation.
need("closesSourceLine" in returns, "exact final-paise return allocation missing")
need("original.lineTotal() - original.returnedAmount()" in returns, "return final allocation does not use exact remaining source-line value")
need("returnedQuantity" in returns and "returnedAmount" in returns, "return allocator does not track previously allocated source-line amount")
need("lockSourceDocument(sales, d.invoiceNo())" in returns and "FOR UPDATE" in returns, "concurrent return creation is not serialized on the source document")

# Quotation optimistic locking across editor and non-editor mutations.
for raw, label in ((quotation_dto, "server quotation DTO"), (quotation_client, "desktop quotation DTO")):
    need("long rowVersion" in raw, f"{label} does not carry rowVersion")
need("WHERE id=? AND row_version=?" in quotation and "row_version=row_version+1" in quotation, "quotation save does not enforce optimistic locking")
need("new ConcurrentEditException(\"Quotation\")" in quotation, "quotation stale-write conflict is not surfaced")
need("quotationRowVersion" in quotation_editor and "outcome.quote().rowVersion()" in quotation_editor, "quotation editor does not retain refreshed row version")
for token in (
    "remarks=?,row_version=row_version+1",
    "follow_up_date=?,row_version=row_version+1",
    "converted_invoice_no=?,row_version=row_version+1",
    "status='DELETED',row_version=row_version+1",
    "row_version=row_version+1 WHERE status NOT IN",
):
    need(token in quotation, f"quotation side-effect mutation does not advance row version: {token}")

# User/admin and permission-matrix concurrency.
need("long rowVersion" in admin_dto and "long rowVersion" in admin_client, "user/admin DTOs do not carry rowVersion")
need("USER_AUTHORITY_LOCK" in admin and "pg_advisory_xact_lock" in admin, "admin authority-changing operations are not serialized")
need("SELECT COALESCE(row_version,0) row_version FROM users WHERE id=? FOR UPDATE" in admin, "user edit does not lock/read current row version")
need("row_version=row_version+1 WHERE id=? AND row_version=?" in admin, "user edit does not enforce optimistic version update")
need("ensureActiveAdministratorRemains" in admin, "last-active-Admin invariant guard missing")
need("role_permission_revision" in migration and "role_permission_revision" in admin, "permission matrix revision authority missing")
need("PermissionSetDto" in admin_dto and "PermissionSetDto" in admin_client, "permission revision DTO missing")
need("permissionRowVersion" in permission and "savePermissions(role, changes, permissionRowVersion)" in permission, "Permission Matrix does not send expected revision")
need("permissionRowVersion" in user_access and "savePermissions(role,permissions.stream()" in user_access, "User Access embedded permission editor does not use revision locking")

# Document Studio optimistic publishing.
need("expectedChecksum" in resource_service and "FOR UPDATE" in resource_service and "ConcurrentEditException" in resource_service, "server resources do not enforce checksum-based optimistic publish")
need("expectedChecksum" in resource_controller and "expectedChecksum" in resource_client, "resource checksum is not carried end-to-end")
need(".server.sha256" in remote_mirror and "expected" in remote_mirror, "Document Studio mirror does not send previous server checksum")
need(".server.sha256" in pdf_remote and "expected" in pdf_remote, "PDF Studio remote store does not send previous server checksum")

# Migration must make new concurrency/precision state durable and startup-verified.
for token in (
    "ALTER TABLE quotation_header",
    "ADD COLUMN IF NOT EXISTS row_version BIGINT",
    "ALTER TABLE users",
    "CREATE TABLE IF NOT EXISTS role_permission_revision",
    "average_unit_cost TYPE NUMERIC(18,4)",
    "unit_cost TYPE NUMERIC(18,4)",
    "unit_cost_snapshot TYPE NUMERIC(18,4)",
):
    need(token in migration, f"financial hardening migration missing: {token}")
need("V9_0_47__financial_multi_user_hardening" in runner, "financial hardening migration is not registered at startup")
need('requireColumn("quotation_header", "row_version")' in runner, "quotation row_version is not startup-verified")
need('requireColumn("users", "row_version")' in runner, "user row_version is not startup-verified")
need('requireTable("role_permission_revision")' in runner, "permission revision table is not startup-verified")

if FAIL:
    print("FINANCIAL_MULTI_USER_CONTRACT_FAIL")
    for item in FAIL:
        print(" -", item)
    sys.exit(1)

print("FINANCIAL_MULTI_USER_CONTRACT_OK calculations=canonical inventory_cost=4dp payments=roundoff-safe returns=exact+serialized quotation_lock=yes admin_lock=yes permissions_lock=yes templates=optimistic")
