from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def text(rel): return (ROOT / rel).read_text(encoding='utf-8', errors='replace')
def req(cond, msg):
    if not cond:
        print(f'FAIL: {msg}', file=sys.stderr)
        raise SystemExit(1)

sales_model = text('desktop/src/main/java/org/example/model/Sales.java')
sales_controller = text('desktop/src/main/java/org/example/controller/SalesController.java')
ops = text('server/src/main/java/org/example/server/operations/BusinessOperationsService.java')
runner = text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
migration = text('server/src/main/resources/db/migration/V9_0_12__sales_tax_mode_compatibility.sql')
sales_payment = text('desktop/src/main/java/org/example/controller/RecordPaymentController.java')
purchase_payment = text('desktop/src/main/java/org/example/controller/PurchasePaymentController.java')
dialog = text('desktop/src/main/java/org/example/util/DialogPresentation.java')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props = text('server/src/main/resources/application.properties')

# Historical blank GST mode compatibility without accepting arbitrary invalid modes.
req('gstType == null || gstType.isBlank() ? "GST" : gstType' in sales_model,
    'Sales model must normalize only null/blank historical GST mode to GST')
req('cmbGstType.getValue().isBlank() ? "GST"' in sales_controller,
    'Sales save must not emit a blank GST mode')
req('String taxType=blank(d.gstType())?"GST":d.gstType()' in ops and 'h.setGstType(taxType)' in ops,
    'server canonical Sale save must normalize blank GST mode before tax calculation/persistence')
req("UPDATE sales_header" in migration and "SET gst_type = 'GST'" in migration and
    "gst_type IS NULL OR BTRIM(gst_type) = ''" in migration,
    'v9.0.12 migration must backfill historical blank Sales gst_type')
req('V9_0_12__sales_tax_mode_compatibility' in runner,
    'runtime migration runner must register the v9.0.12 Sales GST compatibility migration')

# Registers must not remain stale after successful payment mutations.
req(sales_payment.count('ScreenRefreshPolicy.invalidate("sales-register")') >= 2,
    'Sales payment create/update must invalidate Sales Register cache')
req(purchase_payment.count('ScreenRefreshPolicy.invalidate("purchase-register")') >= 2,
    'Purchase payment create/update must invalidate Purchase Register cache')

# Known technical exception strings must be translated before error detail is shown.
req('userFacingErrorDetail(stripHttpMarker(message))' in dialog and
    'lower.startsWith("unsupported tax mode")' in dialog and
    'lower.equals("empty string")' in dialog and
    'DSE ERP technical error detail:' in dialog,
    'shared dialog renderer must sanitize known technical error details and log the raw detail')

# Release identity.
req('APP_VERSION = "9.0.77"' in runtime and 'BUILD_REVISION = "9.0.77"' in runtime,
    'shared runtime identity must be 9.0.77')
req('dse.app.version=9.0.77' in props and 'dse.build.revision=9.0.77' in props,
    'server runtime identity must be 9.0.77')

print('SALES_PAYMENT_COMPATIBILITY_OK version=9.0.77')
