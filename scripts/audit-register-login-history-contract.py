#!/usr/bin/env python3
from release_version import VERSION
from pathlib import Path
import hashlib
ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def req(cond, msg):
    if not cond:
        raise SystemExit('FAIL: ' + msg)

def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
sales_controller = read(Path('desktop/src/main/java/org/example/controller/SalesController.java'))
purchase_controller = read(Path('desktop/src/main/java/org/example/controller/PurchaseController.java'))
return_editor = read(Path('desktop/src/main/java/org/example/service/ReturnEditorService.java'))
return_refund = read(Path('desktop/src/main/java/org/example/controller/ReturnRefundController.java'))
sales_returns = read(Path('desktop/src/main/java/org/example/controller/SalesReturnsController.java'))
sales_fxml = read(Path('desktop/src/main/resources/fxml/pages/SalesList.fxml'))
purchase_fxml = read(Path('desktop/src/main/resources/fxml/pages/PurchaseList.fxml'))
business = read(Path('server/src/main/java/org/example/server/operations/BusinessOperationsService.java'))
import_service = read(Path('desktop/src/main/java/org/example/service/ImportService.java'))
import_controller = read(Path('desktop/src/main/java/org/example/controller/ImportController.java'))
import_result_policy = read(Path('desktop/src/main/java/org/example/importing/ImportResultPolicy.java'))
recon_controller = read(Path('desktop/src/main/java/org/example/controller/PurchaseReconController.java'))
recon_fxml = read(Path('desktop/src/main/resources/fxml/pages/PurchaseRecon.fxml'))
perm = read(Path('desktop/src/main/java/org/example/controller/PermissionMatrixController.java'))
css = read(Path('desktop/src/main/resources/css/light-theme.css'))
login = read(Path('desktop/src/main/java/org/example/controller/LoginController.java'))
bank_fxml = read(Path('desktop/src/main/resources/fxml/pages/BankStatement.fxml'))
bank_controller = read(Path('desktop/src/main/java/org/example/controller/BankStatementController.java'))
runtime = read(Path('shared/src/main/java/org/example/shared/RuntimeContract.java'))
props = read(Path('server/src/main/resources/application.properties'))
root_pom = read(Path('pom.xml'))
app_version = read(Path('desktop/src/main/resources/app-version.properties'))
update = read(Path('desktop/src/main/java/org/example/update/UpdateService.java'))
dynamic_table = read(Path('desktop/src/main/java/org/example/util/DynamicTableLayoutManager.java'))
req('ScreenRefreshPolicy.invalidate("sales-register")' in sales_controller, 'Sale save must invalidate Sales Register')
req('ScreenRefreshPolicy.invalidate("purchase-register")' in purchase_controller, 'Purchase save must invalidate Purchase Register')
for token in ['ScreenRefreshPolicy.invalidate("sales-returns")', 'ScreenRefreshPolicy.invalidate("sales-register")', 'ScreenRefreshPolicy.invalidate("purchase-returns")', 'ScreenRefreshPolicy.invalidate("purchase-register")']:
    req(token in return_editor, f'Return save missing refresh invalidation: {token}')
req('invalidateReturnViews()' in return_refund and 'ScreenRefreshPolicy.invalidate("sales-returns")' in return_refund, 'Return refunds must invalidate return/register views')
req('allSales.isEmpty() || (reusedFromCache && ScreenRefreshPolicy.shouldRefresh("sales-register"' in read(Path('desktop/src/main/java/org/example/controller/SalesListController.java')), 'Sales Register must keep cached data and reload only when empty or stale')
req('all.isEmpty()||(reusedFromCache&&ScreenRefreshPolicy.shouldRefresh("sales-returns"' in sales_returns, 'Sales Return Register must keep cached data and honor invalidation/staleness')
req('text="Pending"' in sales_fxml and 'text="Total Pending"' in sales_fxml, 'Sales Register must use Pending wording')
req('text="Pending"' in purchase_fxml, 'Purchase Register must use Pending wording')
req('salesSummary(where,includeCharts)' in business and 'private SalesPageSummary salesSummary(SqlWhere where,boolean includeCharts)' in business, 'Sales KPIs/totals must use current register filters through the combined summary query')
req('purchaseSummary(where)' in business and 'private PurchasePageSummary purchaseSummary(SqlWhere where)' in business, 'Purchase KPIs/totals must use current register filters through the combined summary query')
req('CREATED WITH WARNING' in import_service and 'Record imported successfully; attachment could not be uploaded' in import_service, 'Sales import must distinguish attachment warning from business-record failure')
req('one or more attachments could not be uploaded' in import_service, 'Purchase import must distinguish attachment warning')
req('Import completed with warnings' in import_controller and 'ImportResultPolicy.hasWarnings' in import_controller and ('hasWarnings' in import_result_policy), 'Import UI must have explicit warning completion state through extracted result policy')
req('Successfully imported records remain saved.' in import_controller, 'Import result must tell user committed records remain saved')
req('text="Linked Ref"' in recon_fxml and 'linkedReferenceLabel' in recon_controller and ('preferredLinkReference' in recon_controller), 'Purchase Recon Linked column must display actual reference identity')
req('new Hyperlink("₹ "+money' not in recon_controller, 'Purchase Recon Linked cell must not use amount as primary link label')
req('special.size() == 1' in perm and 'new CheckBox()' in perm, 'Single Special permission must render as checkbox')
req('menu.setText(granted + " / " + special.size())' in perm, 'Multiple Special permissions must render readable granted/total value')
req('.permission-matrix-table .table-cell .permission-special-menu' in css and '-fx-max-width: 116px;' in css, 'Special permission menu must override generic 38px table action width')
req('btnLogin.setDefaultButton(true)' in login and 'txtPassword.setOnAction(event -> login())' in login, 'Password Enter must submit Login')
req('txtOtp.setOnAction(event -> login())' in login, 'OTP Enter must submit verification')
req('SecretValueCodec.encrypt(password)' in login and 'SecretValueCodec.decrypt(encrypted)' in login, 'Remember Me password must be encrypted at rest using SecretValueCodec')
req('PREF_PASSWORD' in login and 'PREFS.remove(PREF_PASSWORD)' in login, 'Remembered encrypted password must be removable/reset-safe')
req('<StackPane fx:id="statementWorkspace"' in bank_fxml and 'bank-statement-history-popup-content' in bank_fxml and ('StackPane.alignment="CENTER_RIGHT"' not in bank_fxml), 'Bank Statement History must use the owned popup workspace contract')
req('colHistoryBank" text="Bank / Account"' in bank_fxml and 'headerWidth(column, heading)' in dynamic_table and ('sampledContentWidth(table, column)' in dynamic_table), 'Bank Statement History Bank/Account column must remain readable through the global dynamic table authority')
req('statementHistoryDialog=new OwnedDialog<>(statementWorkspace)' in bank_controller and 'setOnCloseRequest(e->restoreStatementHistoryDrawer())' in bank_controller and ('setDividerPositions' not in bank_controller), 'Browse Statements must use an independent owned popup')
req('historyTextCell()' in bank_controller and 'new Tooltip(text)' in bank_controller, 'History text columns must retain full values via tooltips')
protected = {'desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java': '0bb413a5666585af1c67beac15e54bb45112eb8cbc3ffcb36dccf70ea0c837f0', 'desktop/src/main/java/org/example/service/InvoicePdfService.java': 'ddc9bd1120388058ae60742f343553c6c0de2634e885360deef8e31298033fa8', 'desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java': '27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082'}
for path, expected in protected.items():
    req(sha(Path(path)) == expected, f'Protected production PDF generator changed: {path}')
print(f'REGISTER_LOGIN_HISTORY_CONTRACT_OK version={VERSION}')
