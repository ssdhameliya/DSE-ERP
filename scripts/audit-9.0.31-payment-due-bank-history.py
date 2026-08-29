from pathlib import Path
from hashlib import sha256
import sys,re
ROOT=Path(__file__).resolve().parents[1]
def text(rel): return (ROOT/rel).read_text(errors='ignore')
def req(label, ok):
    if not ok:
        print('FAIL:',label); sys.exit(1)
    print('PASS:',label)
sales=text('desktop/src/main/java/org/example/controller/SalesListController.java')
purchase=text('desktop/src/main/java/org/example/controller/PurchaseListController.java')
bank=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
bankf=text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
dash=(ROOT/'desktop/src/main/resources/fxml/pages/Dashboard.fxml').read_bytes()
req('9.0.34 runtime identity', 'APP_VERSION = "9.0.34"' in runtime and 'BUILD_REVISION = "9.0.34"' in runtime)
req('Sales Payment Status does not reuse Payment Due formatter', 'if("RETURN PENDING".equals(status))return "Return Pending";' in sales and 'if("RETURN PENDING".equals(status))return dueLabel(sale);' not in sales)
req('Purchase Payment Status does not reuse Payment Due formatter', 'if("RETURN PENDING".equals(status))return"Return Pending";' in purchase and 'if("RETURN PENDING".equals(status))return due(p);' not in purchase)
req('Return Paid status is a status, not a due label', 'return "Return Paid"' in sales and 'return"Return Paid"' in purchase)
req('Payment Due owns settlement/approval timing wording', 'return "Awaiting Approval"' in sales and 'return "Settled"' in sales and 'return"Awaiting Approval"' in purchase and 'return"Settled"' in purchase)
req('Legacy Sales saved OVERDUE payment view is neutralized after Due-filter removal', '"OVERDUE".equalsIgnoreCase(savedPayment)' in sales and 'savedPayment="All"' in sales and 'cmbPaymentDue' not in sales)
req('Purchase local payment matcher no longer treats OVERDUE as a payment state', 'if("OVERDUE".equals(filter))' not in purchase)
req('OVERDUE removed from Payment Status dropdowns', '"PAID","OVERDUE","RETURN APPROVAL PENDING"' not in sales and '"PAID","OVERDUE","RETURN APPROVAL PENDING"' not in purchase)
req('Payment Due remains a table/display concept after filter removal', 'dueLabel(' in sales and 'private String due(' in purchase and 'cmbPaymentDue' not in sales and 'cmbPaymentDue' not in purchase)
req('Bank history FXML is popup-hosted content rather than a right-side overlay', '<StackPane fx:id="statementWorkspace"' in bankf and '<SplitPane fx:id="statementWorkspace"' not in bankf and 'bank-statement-history-popup-content' in bankf and 'StackPane.alignment="CENTER_RIGHT"' not in bankf)
req('Bank history controller opens a real owned popup', 'statementHistoryDialog=new OwnedDialog<>(statementWorkspace)' in bank and 'setOnCloseRequest(e->restoreStatementHistoryDrawer())' in bank and 'setDividerPositions' not in bank and 'width*.55' not in bank)
req('Bank history popup has explicit usable workspace sizing', 'getDialogPane().setPrefSize(1080,720)' in bank and 'getDialogPane().setMinSize(900,580)' in bank)
req('Dashboard Global Search/layout remains protected', sha256(dash).hexdigest()=='afe7a8e641dd7e1d3574fd8a5d912228837b0129456bc5bc11b823550d8aec8f')
print('PASS: DSE ERP 9.0.34 payment/due separation + Bank Statement history root-cause contract')
