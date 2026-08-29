from pathlib import Path
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)

def pos(s,*needles):
    return [s.index(n) if n in s else -1 for n in needles]

sales_f=text('desktop/src/main/resources/fxml/pages/SalesList.fxml')
pur_f=text('desktop/src/main/resources/fxml/pages/PurchaseList.fxml')
quo_f=text('desktop/src/main/resources/fxml/pages/Quotations.fxml')
dash_f=(ROOT/'desktop/src/main/resources/fxml/pages/Dashboard.fxml').read_bytes()
returns_s_f=text('desktop/src/main/resources/fxml/pages/SalesReturns.fxml')
returns_p_f=text('desktop/src/main/resources/fxml/pages/PurchaseReturns.fxml')
sales_r=text('desktop/src/main/java/org/example/controller/SalesReturnsController.java')
pur_r=text('desktop/src/main/java/org/example/controller/PurchaseReturnsController.java')
pur_ctl=text('desktop/src/main/java/org/example/controller/PurchaseListController.java')
pur_svc=text('desktop/src/main/java/org/example/service/PurchaseService.java')
op_api=text('desktop/src/main/java/org/example/api/operations/OperationsApiClient.java')
op_ctl=text('server/src/main/java/org/example/server/operations/BusinessOperationsController.java')
op_svc=text('server/src/main/java/org/example/server/operations/BusinessOperationsService.java')
finance_f=text('desktop/src/main/resources/fxml/pages/BankExpense.fxml')
finance_c=text('desktop/src/main/java/org/example/controller/BankExpenseController.java')
bank_f=text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
bank_c=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
dash_c=text('desktop/src/main/java/org/example/controller/DashboardController.java')
reg_ui=text('desktop/src/main/java/org/example/util/RegisterUiSupport.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')

req('APP_VERSION = "9.0.34"' in runtime and 'BUILD_REVISION = "9.0.34"' in runtime,'9.0.34 runtime identity')
# Dashboard Global Search FXML must be byte-identical to protected 9.0.28 baseline.
req(sha256(dash_f).hexdigest()=='afe7a8e641dd7e1d3574fd8a5d912228837b0129456bc5bc11b823550d8aec8f','Dashboard Global Search/layout FXML must remain untouched')
for name,f,primary in [('Sales',sales_f,'fx:id="btnNewSale"'),('Purchase',pur_f,'fx:id="btnNewPurchase"'),('Quotation',quo_f,'fx:id="btnNewQuotation"')]:
    a,b=pos(f,'register-header-search-shell',primary)
    req(a>=0 and b>a,f'{name} header search must sit immediately before the primary New action')
    req('register-header-search-icon' in f,f'{name} header search needs the search icon shell')
# visible Sales/Purchase filters + hidden advanced.
for name,f,party in [('Sales',sales_f,'Customer'),('Purchase',pur_f,'Supplier')]:
    for label in [party,'From Date','To Date','Document Status','Payment Status']:
        req(f'text="{label}"' in f,f'{name} visible filter must include {label}')
    req('fx:id="advancedFilters"' in f and 'visible="false" managed="false"' in f,f'{name} Advanced Filters must remain hidden')
    req('text="Payment Due"' not in f or 'fx:id="colDue"' in f,f'{name} visible filter must not require a Payment Due control')
    req('fx:id="cmbPaymentDue"' not in f,f'{name} Payment Due filter is removed from the toolbar')
    req('text="All Date"' in f and 'quick-range-all' in f,f'{name} must expose All Date')
req('text="Payment Status"' not in quo_f and 'text="Payment Due"' not in quo_f,'Quotation must not invent payment filters')
req('text="Customer"' in quo_f and 'text="From Date"' in quo_f and 'text="To Date"' in quo_f and 'text="Document Status"' in quo_f,'Quotation visible filters')
# Purchase Payment Due must be carried desktop -> API -> server and export.
req('cmbPaymentDue' not in pur_ctl and 'dueFilter="All"' in pur_ctl,'Purchase UI/export must not expose or apply a Payment Due filter')
req('String due' in pur_svc and 'purchasesPage(page,size,q,supplier,from,to,paymentStatus,due,mail,documentStatus)' in pur_svc,'Purchase service must carry due filter')
req('&due=' in op_api and '@RequestParam(defaultValue="") String due' in op_ctl,'Purchase API must carry due filter')
req('dueFilter=up(due)' in op_svc and 'NEXT 30 DAYS' in op_svc,'Purchase server must enforce due filters')
# Return side-panel scope: PDF/email removed only there; row action capabilities preserved; notes removed.
for name,f,c,party in [('Sales',returns_s_f,sales_r,'Customer'),('Purchase',returns_p_f,pur_r,'Supplier')]:
    req('View / Print PDF' not in f and f'Email {party}' not in f,f'{name} Return drawer must remove PDF/email buttons')
    req('btnApproveSelected' in f and 'btnRejectSelected' in f and 'Edit Reason' in f,f'{name} Return drawer must expose Edit Reason + existing Approve/Reject')
    req('add("Print / PDF"' in c and 'add("Send Email"' in c,f'{name} Return row action PDF/email functions must remain')
    req('Notes / Remarks' not in c,f'{name} Return action list must remove Notes/Remarks')
    req('canCancelOrDelete(current)' in c and 'row.refund()<=0.0001' in c,f'{name} Cancel/Delete UI must respect refund lifecycle')
# Bank/Expense explicit filters and feedback.
for token in ['financeSearchIcon','filterFrom','filterTo','btnResetFilters','btnRefreshEntries']:
    req(token in finance_f and token in finance_c,f'Bank/Expense filter contract: {token}')
req('periodFilter' not in finance_f and 'financeService.page' in finance_c and 'from,to' in finance_c,'Bank/Expense uses From/To dates instead of period selector')
req('refreshWithFeedback' in finance_c and 'ToastManager.info' in finance_c,'Bank/Expense explicit refresh feedback')
# Sidebar reflow / adaptive bank history.
req('RegisterUiSupport.reflowAfterShellResize(contentPane)' in dash_c and 'reflowAfterShellResize' in reg_ui,'Sidebar must force active workspace reflow')
req('statementHistoryDialog=new OwnedDialog<>(statementWorkspace)' in bank_c and 'setOnCloseRequest(e->restoreStatementHistoryDrawer())' in bank_c and 'setDividerPositions' not in bank_c and 'width*.55' not in bank_c,'Bank Statement history must use a window popup rather than a side overlay')
req('fx:id="statementHistoryDrawer"' in bank_f and 'bank-statement-history-popup-content' in bank_f and 'StackPane.alignment="CENTER_RIGHT"' not in bank_f,'Bank Statement history content must be popup-friendly rather than right-drawer constrained')
req('refreshWithFeedback' in bank_c and 'onAction="#refreshWithFeedback"' in bank_f,'Bank Statement refresh feedback')
print('PASS: DSE ERP 9.0.34 register/workspace UI scope contract')
