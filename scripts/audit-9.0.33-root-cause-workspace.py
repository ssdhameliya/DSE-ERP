from pathlib import Path
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8',errors='replace')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)

runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
qedit=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
qapi=text('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java')
qserver=text('server/src/main/java/org/example/server/quotation/QuotationService.java')
qctrl=text('server/src/main/java/org/example/server/quotation/QuotationController.java')
qf=text('desktop/src/main/resources/fxml/pages/QuotationEditor.fxml')
css=text('desktop/src/main/resources/css/light-theme.css')
finance=text('desktop/src/main/java/org/example/controller/BankExpenseController.java')
bank=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
bankf=text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
dash=text('desktop/src/main/java/org/example/controller/DashboardHomeController.java')
dashf=text('desktop/src/main/resources/fxml/pages/DashboardHome.fxml')
ui=text('desktop/src/main/java/org/example/util/OperationalUiSupport.java')
controllers='\n'.join(p.read_text(errors='ignore') for p in (ROOT/'desktop/src/main/java/org/example/controller').glob('*.java'))
dash_global=(ROOT/'desktop/src/main/resources/fxml/pages/Dashboard.fxml').read_bytes()

req('APP_VERSION = "9.0.34"' in runtime and 'BUILD_REVISION = "9.0.34"' in runtime,'9.0.34 runtime identity')
req('/editor-bootstrap' in qctrl and 'editorBootstrap(Integer id)' in qserver,'Quotation editor has one server bootstrap endpoint')
req('api.editorBootstrap(requestedId)' in qedit and 'partyService.getByType("CUSTOMER")' not in qedit,'Quotation first-open Customer/Source no longer depends on multi-request desktop bootstrap')
req('CustomerChoiceDto' in qapi and 'EditorBootstrapDto' in qapi,'Quotation bootstrap API carries Customer and Source choices together')
req('cmbCustomer.setDisable(true)' in qedit and 'cmbSource.setDisable(true)' in qedit and 'cmbCustomer.setDisable(false);cmbSource.setDisable(false);' in qedit,'Quotation Master controls have deterministic loading/enable lifecycle')
req('<padding><Insets top="14" right="14" bottom="14" left="14"/></padding>' in qf and 'quotation-editor-root-padded-content' in qf,'Quotation root owns Sale/Purchase-style 14px page gutter')
req('.quotation-editor-content.quotation-editor-root-padded-content' in css and '-fx-padding: 0;' in css,'Quotation center content does not double-apply page padding')

req(finance.count('RegisterUiSupport.setCurrentYearRange(filterFrom,filterTo,today)')>=2 or finance.count('filterFrom.setValue(today.withDayOfYear(1))')>=2,'Bank/Expense initialize and Reset use start of current year')
req('filterFrom.setValue(today.minusMonths(3))' not in finance,'Bank/Expense old rolling 3-month default removed')

req('<StackPane fx:id="statementWorkspace"' in bankf and '<SplitPane fx:id="statementWorkspace"' not in bankf,'Bank Statement history no longer competes in a SplitPane')
req('bank-statement-history-popup-content' in bankf and 'StackPane.alignment="CENTER_RIGHT"' not in bankf,'Bank Statement history is popup content rather than a right overlay')
req('statementHistoryDialog=new OwnedDialog<>(statementWorkspace)' in bank and 'setDividerPositions' not in bank and 'width*.55' not in bank,'Bank Statement controller opens History in an owned popup without divider clamps')

req('public static void focusWorkArea(Node preferred)' in ui,'neutral work-area focus helper exists')
req('OperationalUiSupport.focusSearch' not in controllers,'operational screens no longer auto-focus Search on navigation')
req('OperationalUiSupport.focusWorkArea' in controllers,'operational screens explicitly focus their table/work area instead')
req(sha256(dash_global).hexdigest()=='afe7a8e641dd7e1d3574fd8a5d912228837b0129456bc5bc11b823550d8aec8f','Dashboard Global Search remains byte-identical to protected baseline')

req('text="Add Item Master"' in dashf and 'text="Create Quotation"' in dashf,'Dashboard Quick Action labels match create workflows')
req('"/fxml/pages/QuotationEditor.fxml"' in dash,'Dashboard Create Quotation opens editor directly')
req('PartyDialog.fxml' in dash and 'controller.configure(type,null)' in dash,'Dashboard Add Customer/Supplier opens add dialog directly')
req('Itemdialog.fxml' in dash and '"Add Item Master"' in dash,'Dashboard Add Item Master opens item create dialog directly')
req('requestNewEntry(BankExpenseController.Mode.BANK)' in dash and 'requestNewEntry(BankExpenseController.Mode.EXPENSE)' in dash,'Dashboard Bank/Expense actions request a new-entry dialog')

print('PASS: DSE ERP 9.0.34 root-cause workspace + dashboard create-action contract')
