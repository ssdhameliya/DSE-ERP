from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8')
def req(ok,msg):
    if not ok: print('FAIL:',msg); sys.exit(1)
    print('PASS:',msg)

sales=text('desktop/src/main/resources/fxml/pages/SalesList.fxml')
purchase=text('desktop/src/main/resources/fxml/pages/PurchaseList.fxml')
quotation=text('desktop/src/main/resources/fxml/pages/Quotations.fxml')
qedit=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
bank=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
css=text('desktop/src/main/resources/css/light-theme.css')
for name,f in [('Sales',sales),('Purchase',purchase),('Quotation',quotation)]:
    req('text="Save View"' in f and 'fx:id="savedViewsMenu"' in f,f'{name}: Save View and Saved Views controls exist')
    req('register-saved-views-menu' in f,f'{name}: Saved Views has readable-width style contract')
    req('Region HBox.hgrow="ALWAYS"' in f,f'{name}: right-side spacer keeps controls at the end of the search/filter panel')
req(sales.index('Region HBox.hgrow="ALWAYS"') < sales.index('text="Save View"'), 'Sales: Save View is after the right-side spacer')
req(purchase.index('Region HBox.hgrow="ALWAYS"') < purchase.index('text="Save View"'), 'Purchase: Save View is after the right-side spacer')
req(quotation.index('Region HBox.hgrow="ALWAYS"') < quotation.index('text="Save View"'), 'Quotation: Save View is after the right-side spacer')
req(quotation.count('fx:id="savedViewsMenu"')==1,'Quotation: exactly one Saved Views control remains')
req('loadCustomerFallback()' in qedit and 'partyService.search("CUSTOMER","",40)' in qedit,'Quotation: Customer has independent master fallback')
req('loadSourceFallback()' in qedit and 'getValuesByCategoryCode("QUOTATION_SOURCE")' in qedit,'Quotation: Source has independent master fallback')
req('statementHistoryDialog=new OwnedDialog<>(statementWorkspace)' in bank and 'restoreStatementHistoryDrawer' in bank,'Bank Statement: History uses owned popup and restores host content on close')
req('.show-hide-column-menu-item' in css and '-fx-min-width: 280px' in css,'Table column chooser: readable menu width and labels')
req('.register-saved-view-item' in css and '-fx-min-width: 300px' in css and '-fx-text-overrun: clip' in css,'Saved Views: popup values have sufficient width and no ellipsis clipping')
print('PASS: DSE ERP 9.0.42 locked register UI contract')
