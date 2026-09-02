#!/usr/bin/env python3
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding='utf-8')
def req(c,m):
    if not c: raise SystemExit('FAIL: '+m)
def sha(p): return hashlib.sha256((ROOT/p).read_bytes()).hexdigest()

qe=read(Path('desktop/src/main/java/org/example/controller/QuotationEditorController.java'))
qef=read(Path('desktop/src/main/resources/fxml/pages/QuotationEditor.fxml'))
qc=read(Path('desktop/src/main/java/org/example/controller/QuotationController.java'))
qf=read(Path('desktop/src/main/resources/fxml/pages/Quotations.fxml'))
qapi=read(Path('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java'))
qs=read(Path('server/src/main/java/org/example/server/quotation/QuotationService.java'))
qsc=read(Path('server/src/main/java/org/example/server/quotation/QuotationController.java'))
qd=read(Path('server/src/main/java/org/example/server/quotation/QuotationDtos.java'))
mig=read(Path('server/src/main/resources/db/migration/V9_0_14__quotation_register_hardening.sql'))
runner=read(Path('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java'))
sales=read(Path('desktop/src/main/java/org/example/controller/SalesController.java'))
purchase=read(Path('desktop/src/main/java/org/example/controller/PurchaseController.java'))
refund=read(Path('desktop/src/main/java/org/example/controller/ReturnRefundController.java'))
sl=read(Path('desktop/src/main/java/org/example/controller/SalesListController.java'))
pl=read(Path('desktop/src/main/java/org/example/controller/PurchaseListController.java'))
slf=read(Path('desktop/src/main/resources/fxml/pages/SalesList.fxml'))
plf=read(Path('desktop/src/main/resources/fxml/pages/PurchaseList.fxml'))
biz=read(Path('server/src/main/java/org/example/server/operations/BusinessOperationsService.java'))
bizc=read(Path('server/src/main/java/org/example/server/operations/BusinessOperationsController.java'))
ops=read(Path('desktop/src/main/java/org/example/api/operations/OperationsApiClient.java'))
imp=read(Path('desktop/src/main/java/org/example/controller/ImportController.java'))
runtime=read(Path('shared/src/main/java/org/example/shared/RuntimeContract.java'))
props=read(Path('server/src/main/resources/application.properties'))
pom=read(Path('pom.xml'))
app=read(Path('desktop/src/main/resources/app-version.properties'))
update=read(Path('desktop/src/main/java/org/example/update/UpdateService.java'))

# Quotation create/edit parity and safe side panel.
req('fx:id="btnSave" text="Save"' in qef and 'fx:id="btnCancel" text="Cancel"' in qef,'Quotation top actions must be Save + Cancel only')
req('Notes &amp; Attachment' in qef and 'onAction="#previewAttachment"' in qef and 'onAction="#removeAttachment"' in qef,'Quotation notes/attachment side panel must support preview/delete')
req('WhatsApp' not in qef,'Quotation editor must not expose WhatsApp side action')
req('fx:id="btnDeleteLine" text="Delete Item"' in qef and 'deleteSelectedLine()' in qe,'Quotation editor must support explicit line deletion')
req('api.searchItems(query,12)' in qe and '/api/quotations/items/search' in qapi and '@GetMapping("/items/search")' in qsc,'Quotation item search must use dedicated Quotation API')
req('QUOTATION.CREATE' in qs and 'QUOTATION.EDIT' in qs and 'itemChoices(String query,int limit)' in qs,'Quotation item search must authorize Quotation create/edit users')

# Master-driven source + historical line descriptions.
req('lookupService.getValuesByCategoryCode("QUOTATION_SOURCE")' in qe and 'lookupService.getValuesByCategoryCode("QUOTATION_SOURCE")' in qc and 'sourceChoices()' in qs,
    'Quotation Source must use the same generic Master category-code path as other Master-backed fields')
req('cmbSource.setItems(FXCollections.observableArrayList("Direct"' not in qe,'Quotation editor must not hard-code Source list')
req("QUOTATION_SOURCE" in qs and "not an active QUOTATION SOURCE Master value" in qs,'Server must validate active Master-driven Quotation Source')
req("QUOTATION_SOURCE" in mig and 'item_description_snapshot' in mig,'v9.0.57 migration must seed source master and snapshot historical descriptions')
req('V9_0_14__quotation_register_hardening' in runner,'runtime migration runner must register v9.0.57 migration')
req('item_description_snapshot' in qs and 'l.description()' in qs,'Quotation save/duplicate must persist historical line description')

# Converted Sale direct navigation and register side WhatsApp removal.
req('btnOpenConvertedSale' in qf and 'onAction="#openConvertedSale"' in qf and 'LinkedRecordContext.open("SALE"' in qc,'Converted Quotation must open linked Sale directly')
req('import org.example.navigation.NavigationManager;' in qc,'QuotationController must import NavigationManager for converted-Sale navigation')
req('<Button text="WhatsApp" onAction="#whatsappSelected"' not in qf,'Quotation detail side panel must not show WhatsApp')

# Preserve transaction values while editing existing lines.
req('selectItem(cachedItem, false)' in sales and 'applyMasterDefaults' in sales,'Sale edit must preserve persisted rate/GST/discount')
req('selectItem(item, false)' in purchase and 'applyMasterDefaults' in purchase,'Purchase edit must preserve persisted rate/GST/discount')
req('selectItem(cached,false)' in qe and 'applyMasterDefaults' in qe,'Quotation edit must preserve persisted quantity/rate/GST/discount')

# Register document-status filters without replacing existing advanced panels.
req('fx:id="cmbDocumentStatus"' in slf and 'advancedFilters' in slf,'Sales Register must add Document Status without removing existing filter panel')
req('fx:id="cmbDocumentStatus"' in plf and 'advancedFilters' in plf,'Purchase Register must add Document Status without removing existing filter panel')
req('documentStatus' in sl and 'documentStatus' in pl and 'documentStatus' in ops and 'documentStatus' in bizc,'Document Status must flow desktop -> API -> server')
req('String documentStatus' in biz and 'docFilter=up(documentStatus)' in biz,'Server Sales/Purchase register must apply Document Status filter')

# Purchase Total Items semantics.
req("COUNT(DISTINCT NULLIF(TRIM(l.item_code),''))" in biz,'Purchase Total Items KPI must count distinct item codes')
req('SUM(l.quantity)' not in biz[biz.find('private OperationDtos.PurchaseMetrics purchaseMetrics'):biz.find('private Map<Integer,Double> saleQuantityTotals')], 'Purchase KPI must not use quantity sum as item count')

# Import no-op success semantics.
req('Import completed — no changes required' in imp and 'failed == 0 && succeeded == 0 && result.skipped > 0' in imp,'Successful skipped/no-op import must not be reported as failed')
req('if (result.failedCount() > 0) return true;' in imp,'Import warnings must distinguish actual failures from duplicate skips')

# Compile regression explicitly called out by user.
req('import org.example.util.ScreenRefreshPolicy;' in refund and 'ScreenRefreshPolicy.invalidate(' in refund,'ReturnRefundController must import ScreenRefreshPolicy')

# Release identity.
req('APP_VERSION = "9.0.57"' in runtime and 'BUILD_REVISION = "9.0.57"' in runtime,'Runtime identity must be 9.0.57')
req('dse.app.version=9.0.57' in props and 'dse.build.revision=9.0.57' in props,'Server identity must be 9.0.57')
req('<version>9.0.57</version>' in pom and '<dse.phase>9.0.57</dse.phase>' in pom,'Maven identity must be 9.0.57')
req('version=9.0.57' in app and 'DEFAULT_VERSION="9.0.57"' in update,'Desktop/update identity must be 9.0.57')

protected={
 'desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java':'5d84c57c22299bfedcc969512b33f2a8cd0371455918ef82f71037827ee2686c',
 'desktop/src/main/java/org/example/service/InvoicePdfService.java':'ddc9bd1120388058ae60742f343553c6c0de2634e885360deef8e31298033fa8',
 'desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java':'27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082',
}
for p,h in protected.items(): req(sha(Path(p))==h,'Protected production PDF generator changed: '+p)
print('PASS: DSE ERP 9.0.57 quotation/register/import/edit-preservation contract')
