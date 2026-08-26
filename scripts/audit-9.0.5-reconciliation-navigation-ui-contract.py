from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def require(condition, message):
    if not condition:
        raise SystemExit('FAIL: ' + message)

bank = text('desktop/src/main/java/org/example/controller/BankStatementController.java')
bank_fxml = text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
finance = text('desktop/src/main/java/org/example/controller/BankExpenseController.java')
finance_fxml = text('desktop/src/main/resources/fxml/pages/BankExpense.fxml')
recon = text('desktop/src/main/java/org/example/controller/PurchaseReconController.java')
recon_fxml = text('desktop/src/main/resources/fxml/pages/PurchaseRecon.fxml')
returns = text('desktop/src/main/java/org/example/controller/PurchaseReturnsController.java')
returns_fxml = text('desktop/src/main/resources/fxml/pages/PurchaseReturns.fxml')
css = text('desktop/src/main/resources/css/ui-components.css')
dark_css = text('desktop/src/main/resources/css/dark-theme.css')
light_css = text('desktop/src/main/resources/css/light-theme.css')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
server_props = text('server/src/main/resources/application.properties')

# Match workspace: no low-confidence first-row auto-allocation; bank and document states are distinct.
require('candidate.confidence()>=75' in bank and 'autoSelect' in bank,
        'Match workspace must only auto-select a genuinely suggested candidate')
require('Document Residual' in bank and 'Bank Remaining' in bank and 'Document Remains Partial' in bank,
        'Match workspace must distinguish bank allocation from document settlement')
require('Confirm Partial Settlement' in bank,
        'Partial document settlement must require explicit confirmation')

# Deep links from finance and Purchase Recon must carry the exact statement transaction id.
require('LinkedRecordContext.open("BANK_STATEMENT",row.statementTransactionId.intValue()' in finance,
        'Bank/Expense Match link must deep-link to the exact Bank Statement transaction')
require('Open Bank Statement' in finance and 'Open Linked ERP Record' in finance,
        'Bank/Expense actions must distinguish statement navigation from ERP document navigation')
require('fx:id="btnBankStatement"' in recon_fxml and 'openBankStatementWorkspace' in recon,
        'Purchase Recon must expose a top-level Bank Statement navigation button')
require('colLinked.setCellFactory' in recon and 'openLinkedAmount(row)' in recon,
        'Purchase Recon Linked amount must be actionable')
require('openBankStatementLink' in recon and 'LinkedRecordContext.open("BANK_STATEMENT"' in recon,
        'Purchase Recon Bank Statement links must deep-link to the exact statement transaction')

# Purchase Return lifecycle/filter behavior.
require('Apply Filters' not in returns_fxml,
        'Purchase Return must not retain the redundant Apply Filters button')
require('dpFrom.setValue(null)' in returns and 'dpTo.setValue(null)' in returns,
        'Purchase Return must default to all dates')
require('dpFrom.valueProperty().addListener' in returns and 'dpTo.valueProperty().addListener' in returns,
        'Purchase Return date changes must auto-apply')
require('onScreenShown(boolean reusedFromCache){org.example.util.OperationalUiSupport.focusSearch(search);load();}' in returns,
        'Purchase Return must reload whenever the screen is shown')

# Bank Statement history scale/master-account contract.
require('fx:id="statementWorkspace"' in bank_fxml and 'fx:id="statementHistoryDrawer" prefWidth="920" minWidth="780" maxWidth="1120"' in bank_fxml,
        'Bank Statement History must use the wider resizable split workspace')
require('fx:id="cmbHistoryAccount" editable="true"' in bank_fxml,
        'Bank Statement History account filter must be a searchable ComboBox')
require('lookupService.getByType("BANK ACCOUNT")' in bank and 'resolveBankAccount' in bank,
        'Bank Statement history/import account ownership must come from BANK ACCOUNT Master Data')
require('OwnedChoiceDialog' in bank and 'Select Bank Account' in bank,
        'Unmatched/blank imported Bank Account must require explicit master selection')

# Finance dialog layout and global selection palette.
require('finance-entry-primary-column' in finance_fxml and 'finance-entry-secondary-column' in finance_fxml,
        'Bank/Expense Add/Edit form must use the available dialog width in two panels')
require('.finance-entry-dialog-card {' in css and '-fx-min-width: 700px;' in css and '-fx-pref-width: 760px;' in css and '-fx-max-width: 860px;' in css,
        'Bank/Expense dialog card must override the older compact finance-entry-card width cap')
require('prepareForLinkedTransactionNavigation();' in bank and 'closeStatementHistory();' in bank and 'tableHistory.getSelectionModel().clearSelection()' in bank,
        'Exact Bank Statement deep links must close stale History state before revealing the target row')
for profile in ('register','responsive','master','history','administration'):
    selector=f'.erp-table-profile-{profile} .table-row-cell:filled:selected'
    require(selector in dark_css and selector in light_css,
            f'Theme-owned purple selection contract missing for {profile} tables')
require('-fx-text-fill: #3b1b68;' not in css[css.find('9.0.5 global record-table selection contract'):css.find('9.0.5 Bank / Expense entry dialogs')],
        'Shared selection contract must not leak light-theme selected text colours into dark mode')

# Runtime/startup compatibility remains one exact value.
require('APP_VERSION = "9.0.14"' in runtime and 'BUILD_REVISION = "9.0.14"' in runtime,
        'Desktop app/build identity must both be current 9.0.14')
require('dse.app.version=9.0.14' in server_props and 'dse.build.revision=9.0.14' in server_props,
        'Spring Boot app/build identity must exactly match desktop 9.0.14')

print('PASS: DSE ERP 9.0.5 reconciliation/navigation/UI contract')
