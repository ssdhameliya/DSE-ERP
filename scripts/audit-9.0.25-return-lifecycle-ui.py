from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
def text(path): return (root/path).read_text(encoding='utf-8')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)
    print('PASS:',msg)

ret = text(Path('server/src/main/java/org/example/server/returns/ReturnService.java'))
ops = text(Path('server/src/main/java/org/example/server/operations/BusinessOperationsService.java'))
runner = text(Path('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java'))
mig = text(Path('server/src/main/resources/db/migration/V9_0_25__return_lifecycle_completion.sql'))
refund = text(Path('desktop/src/main/java/org/example/controller/ReturnRefundController.java'))
sales = text(Path('desktop/src/main/java/org/example/controller/SalesListController.java'))
purchase = text(Path('desktop/src/main/java/org/example/controller/PurchaseListController.java'))
pr = text(Path('desktop/src/main/java/org/example/controller/PurchaseReturnsController.java'))
ss = text(Path('desktop/src/main/java/org/example/service/SalesService.java'))
ps = text(Path('desktop/src/main/java/org/example/service/PurchaseService.java'))
runtime = text(Path('shared/src/main/java/org/example/shared/RuntimeContract.java'))
props = text(Path('server/src/main/resources/application.properties'))

req('APP_VERSION = "9.0.28"' in runtime and 'BUILD_REVISION = "9.0.28"' in runtime,
    'desktop/shared release identity is 9.0.28')
req('dse.app.version=9.0.28' in props and 'dse.build.revision=9.0.28' in props,
    'server release identity is 9.0.28')
req('V9_0_25__return_lifecycle_completion' in runner,
    '9.0.28 lifecycle cleanup migration is registered')
req("refund_status='WAITING APPROVAL'" in mig and "refund_status='N/A'" in mig,
    'migration normalizes waiting and terminal refund states')

req('requireNoOpenReturn' in ret and 'still has a refund/settlement balance' in ret,
    'server blocks a second return while approval/refund is still open')
req('RETURN APPROVAL PENDING' in ret and 'WAITING APPROVAL' in ret,
    'pending approval Return is exposed to original document lifecycle')
req("status='REJECTED',refund_status='N/A'" in ret,
    'rejected Return clears financial lifecycle')
req("status='CANCELLED',refund_status='N/A'" in ret and "status='DELETED',refund_status='N/A'" in ret,
    'cancel/delete clear active refund lifecycle')
req('lifecycleRefundStatus' in ret and 'return "N/A"' in ret,
    'Return Register refund status respects document lifecycle')
stock_section = ops.split("FROM return_register WHERE item_code=?",1)[1].split('ORDER BY movement_day DESC',1)[0]
req("UPPER(COALESCE(status,'PENDING APPROVAL'))='APPROVED'" in stock_section,
    'stock movement history includes approved Returns only')
req('currentBalanceSql' in ops and 'currentDueDateSql' in ops and 'RETURN APPROVAL PENDING' in ops,
    'register filters/KPIs use effective Return amount/due/status')

req('implements ScreenLifecycle' in refund and 'onScreenShown(boolean reusedFromCache)' in refund,
    'refund screen participates in navigation lifecycle')
# initialize must not perform business data load; onScreenShown must.
init_part = refund.split('initialize()',1)[1].split('}',1)[0] if 'initialize()' in refund else ''
req('load();' not in init_part and 'onScreenShown(boolean reusedFromCache)' in refund and 'load();' in refund.split('onScreenShown(boolean reusedFromCache)',1)[1].split('}',1)[0],
    'refund record reloads on every screen show instead of only first initialization')
req(pr.count('ScreenRefreshPolicy.invalidate("purchase-register")') >= 4,
    'Purchase Return approve/reject/cancel/delete invalidate Purchase Register')
req('Unable to load authoritative Sales Return lifecycle state' in ss and 'Unable to load authoritative Purchase Return lifecycle state' in ps,
    'Sale/Purchase do not silently fall back to stale PAID state when Return overlay fails')

for src,name in ((sales,'Sales'),(purchase,'Purchase')):
    req('RETURN APPROVAL PENDING' in src, f'{name} register supports Return Pending Approval')
    req('dpFrom.setValue(null)' in src and 'dpTo.setValue(null)' in src,
        f'{name} register defaults to all records when advanced date filter is hidden')

for rel,name in ((Path('desktop/src/main/resources/fxml/pages/SalesList.fxml'),'Sales'),
                 (Path('desktop/src/main/resources/fxml/pages/PurchaseList.fxml'),'Purchase')):
    data=text(rel)
    ET.parse(root/rel)
    req(data.count('fx:id="cmbPaymentStatus"') == 1 and data.count('fx:id="cmbDocumentStatus"') == 1,
        f'{name} toolbar has one Document Status and one Payment Status control')
    req('fx:id="advancedFilters"' in data and 'visible="false"' in data and 'managed="false"' in data,
        f'{name} Advanced Filter panel is hidden/unmanaged')
    req('Advanced Filters' not in data,
        f'{name} does not expose an Advanced Filters button')

print('PASS: DSE ERP 9.0.28 Return lifecycle + register UI contract')
