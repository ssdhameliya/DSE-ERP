#!/usr/bin/env python3
from pathlib import Path
import hashlib

ROOT=Path(__file__).resolve().parents[1]
def read(path): return (ROOT/path).read_text(encoding='utf-8')
def req(cond,msg):
    if not cond: raise SystemExit('FAIL: '+msg)

def sha(path): return hashlib.sha256((ROOT/path).read_bytes()).hexdigest()

sales_controller=read(Path('desktop/src/main/java/org/example/controller/SalesController.java'))
purchase_controller=read(Path('desktop/src/main/java/org/example/controller/PurchaseController.java'))
return_editor=read(Path('desktop/src/main/java/org/example/service/ReturnEditorService.java'))
return_refund=read(Path('desktop/src/main/java/org/example/controller/ReturnRefundController.java'))
sales_returns=read(Path('desktop/src/main/java/org/example/controller/SalesReturnsController.java'))
sales_fxml=read(Path('desktop/src/main/resources/fxml/pages/SalesList.fxml'))
purchase_fxml=read(Path('desktop/src/main/resources/fxml/pages/PurchaseList.fxml'))
business=read(Path('server/src/main/java/org/example/server/operations/BusinessOperationsService.java'))
import_service=read(Path('desktop/src/main/java/org/example/service/ImportService.java'))
import_controller=read(Path('desktop/src/main/java/org/example/controller/ImportController.java'))
recon_controller=read(Path('desktop/src/main/java/org/example/controller/PurchaseReconController.java'))
recon_fxml=read(Path('desktop/src/main/resources/fxml/pages/PurchaseRecon.fxml'))
perm=read(Path('desktop/src/main/java/org/example/controller/PermissionMatrixController.java'))
css=read(Path('desktop/src/main/resources/css/ui-components.css'))
login=read(Path('desktop/src/main/java/org/example/controller/LoginController.java'))
bank_fxml=read(Path('desktop/src/main/resources/fxml/pages/BankStatement.fxml'))
bank_controller=read(Path('desktop/src/main/java/org/example/controller/BankStatementController.java'))
runtime=read(Path('shared/src/main/java/org/example/shared/RuntimeContract.java'))
props=read(Path('server/src/main/resources/application.properties'))
root_pom=read(Path('pom.xml'))
app_version=read(Path('desktop/src/main/resources/app-version.properties'))
update=read(Path('desktop/src/main/java/org/example/update/UpdateService.java'))

# Mutation-driven register refresh.
req('ScreenRefreshPolicy.invalidate("sales-register")' in sales_controller,'Sale save must invalidate Sales Register')
req('ScreenRefreshPolicy.invalidate("purchase-register")' in purchase_controller,'Purchase save must invalidate Purchase Register')
for token in ['ScreenRefreshPolicy.invalidate("sales-returns")','ScreenRefreshPolicy.invalidate("sales-register")','ScreenRefreshPolicy.invalidate("purchase-returns")','ScreenRefreshPolicy.invalidate("purchase-register")']:
    req(token in return_editor, f'Return save missing refresh invalidation: {token}')
req('invalidateReturnViews()' in return_refund and 'ScreenRefreshPolicy.invalidate("sales-returns")' in return_refund,
    'Return refunds must invalidate return/register views')
req('reusedFromCache || allSales.isEmpty()' in read(Path('desktop/src/main/java/org/example/controller/SalesListController.java')), 'Sales Register must reload whenever user returns to the cached page')
req('reusedFromCache||all.isEmpty()' in sales_returns and 'shouldRefresh("sales-returns"' in sales_returns,'Sales Return Register must reload whenever user returns and honor invalidation/staleness')

# Outstanding wording and KPI/filter alignment.
req('text="Pending"' in sales_fxml and 'text="Total Pending"' in sales_fxml,'Sales Register must use Pending wording')
req('text="Pending"' in purchase_fxml,'Purchase Register must use Pending wording')
req('salesMetrics(where)' in business and 'private OperationDtos.SalesMetrics salesMetrics(SqlWhere where)' in business,
    'Sales KPIs must use current register filters')
req('purchaseMetrics(where)' in business and 'private OperationDtos.PurchaseMetrics purchaseMetrics(SqlWhere where)' in business,
    'Purchase KPIs must use current register filters')

# Import result semantics: committed record must stay success with warning for post-save attachments.
req('CREATED WITH WARNING' in import_service and 'Record imported successfully; attachment could not be uploaded' in import_service,
    'Sales import must distinguish attachment warning from business-record failure')
req('one or more attachments could not be uploaded' in import_service,'Purchase import must distinguish attachment warning')
req('Import completed with warnings' in import_controller and 'hasImportWarnings' in import_controller,
    'Import UI must have explicit warning completion state')
req('Successfully imported records remain saved.' in import_controller,'Import result must tell user committed records remain saved')

# Purchase Recon link identity instead of amount.
req('text="Linked Ref"' in recon_fxml and 'linkedReferenceLabel' in recon_controller and 'preferredLinkReference' in recon_controller,
    'Purchase Recon Linked column must display actual reference identity')
req('new Hyperlink("₹ "+money' not in recon_controller,'Purchase Recon Linked cell must not use amount as primary link label')

# Permission Matrix Special: dash / checkbox / readable n / total.
req('special.size() == 1' in perm and 'new CheckBox()' in perm,'Single Special permission must render as checkbox')
req('menu.setText(granted + " / " + special.size())' in perm,'Multiple Special permissions must render readable granted/total value')
req('.permission-matrix-table .table-cell .permission-special-menu' in css and '-fx-max-width: 116px;' in css,
    'Special permission menu must override generic 38px table action width')

# Keyboard login and secure remembered password.
req('btnLogin.setDefaultButton(true)' in login and 'txtPassword.setOnAction(event -> login())' in login,
    'Password Enter must submit Login')
req('txtOtp.setOnAction(event -> login())' in login,'OTP Enter must submit verification')
req('SecretValueCodec.encrypt(password)' in login and 'SecretValueCodec.decrypt(encrypted)' in login,
    'Remember Me password must be encrypted at rest using SecretValueCodec')
req('PREF_PASSWORD' in login and 'PREFS.remove(PREF_PASSWORD)' in login,
    'Remembered encrypted password must be removable/reset-safe')

# Bank Statement History width/readability.
req('fx:id="statementHistoryDrawer" prefWidth="920" minWidth="780" maxWidth="1120"' in bank_fxml,
    'Bank Statement History drawer must be substantially wider')
req('colHistoryBank" text="Bank / Account" minWidth="185" prefWidth="235"' in bank_fxml,
    'Bank Statement History Bank/Account column must remain readable')
req('statementWorkspace.setDividerPositions(.46)' in bank_controller,'Browse Statements must allocate majority width to history drawer')
req('historyTextCell()' in bank_controller and 'new Tooltip(text)' in bank_controller,
    'History text columns must retain full values via tooltips')

# Exact current release identity.
req('APP_VERSION = "9.0.28"' in runtime and 'BUILD_REVISION = "9.0.28"' in runtime,'Runtime identity must be 9.0.18')
req('dse.app.version=9.0.28' in props and 'dse.build.revision=9.0.28' in props,'Server identity must be 9.0.18')
req('<version>9.0.28</version>' in root_pom and '<dse.phase>9.0.28</dse.phase>' in root_pom,'Maven identity must be 9.0.18')
req('version=9.0.28' in app_version and 'DEFAULT_VERSION="9.0.28"' in update,'Desktop/updater identity must be 9.0.18')

# Locked production document generators must remain identical to v9.0.12 hashes.
protected={
 'desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java':'5d84c57c22299bfedcc969512b33f2a8cd0371455918ef82f71037827ee2686c',
 'desktop/src/main/java/org/example/service/InvoicePdfService.java':'349e9f1f863c122826cad2560091f2dac87cdb9b2bcb0a42f5825fd312feb778',
 'desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java':'27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082',
}
for path,expected in protected.items(): req(sha(Path(path))==expected, f'Protected production PDF generator changed: {path}')

print('PASS: DSE ERP 9.0.18 register/KPI/import/recon/permission/login/history contract')
