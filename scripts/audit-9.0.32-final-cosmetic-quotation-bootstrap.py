from pathlib import Path
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8',errors='replace')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)

sales_f=text('desktop/src/main/resources/fxml/pages/SalesList.fxml')
pur_f=text('desktop/src/main/resources/fxml/pages/PurchaseList.fxml')
quo_f=text('desktop/src/main/resources/fxml/pages/Quotations.fxml')
sales=text('desktop/src/main/java/org/example/controller/SalesListController.java')
purchase=text('desktop/src/main/java/org/example/controller/PurchaseListController.java')
editor=text('desktop/src/main/java/org/example/controller/QuotationEditorController.java')
nav=text('desktop/src/main/java/org/example/navigation/NavigationManager.java')
css=text('desktop/src/main/resources/css/ui-components.css')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
dash=(ROOT/'desktop/src/main/resources/fxml/pages/Dashboard.fxml').read_bytes()

req('APP_VERSION = "9.0.34"' in runtime and 'BUILD_REVISION = "9.0.34"' in runtime,'9.0.34 runtime identity')
req(sha256(dash).hexdigest()=='afe7a8e641dd7e1d3574fd8a5d912228837b0129456bc5bc11b823550d8aec8f','Dashboard Global Search must remain untouched')
for name,f,icon in [('Sales',sales_f,'salesHeaderSearchIcon'),('Purchase',pur_f,'purchaseHeaderSearchIcon'),('Quotation',quo_f,'quotationHeaderSearchIcon')]:
    req('erp-item-search-shell,register-header-search-shell' in f,f'{name} header search must reuse Item Master search shell')
    req('approved-input,erp-item-search,register-header-search-input' in f,f'{name} header search field must reuse Item Master search styling')
    req(f'fx:id="{icon}"' in f and 'StackPane.alignment="CENTER_RIGHT"' in f and '<Insets right="8"/>' in f,f'{name} search icon must be embedded on the right')
req('-fx-padding: 7px 40px 7px 10px;' in css,'register search must reserve right-side icon padding')

for name,f,c in [('Sales',sales_f,sales),('Purchase',pur_f,purchase)]:
    req('fx:id="cmbPaymentDue"' not in f,f'{name} Payment Due filter must be removed from FXML')
    req('cmbPaymentDue' not in c,f'{name} controller must not keep a hidden Payment Due filter')
    req('fx:id="colDue" text="Payment Due"' in f,f'{name} Payment Due table column must remain')
    req('"All"' in c,f'{name} server calls retain no-filter due compatibility')

req('prepareMasterControlsForBootstrap();' in editor,'Create Quotation must prepare Customer/Source controls before bootstrap')
req('cmbCustomer.setDisable(true);' in editor and 'cmbSource.setDisable(true);' in editor,'Create Quotation must prevent first-click races while Master data loads')
req('api.editorBootstrap(requestedId)' in editor,'Create Quotation must load Customer/Source through one server bootstrap request')
req('EditorBootstrapDto' in text('desktop/src/main/java/org/example/api/quotation/QuotationApiClient.java'),'Create Quotation bootstrap must return all active Master choices together')
req('cmbCustomer.setDisable(false);cmbSource.setDisable(false);' in editor,'Create Quotation must enable Master controls only after bootstrap is applied')
req('Loading customers...' in editor and 'Loading sources...' in editor,'Create Quotation must show deterministic loading state')
req('"/fxml/pages/QuotationEditor.fxml"' in nav and 'NON_CACHEABLE' in nav,'Quotation editor must use the same fresh non-cached lifecycle as Sale/Purchase')
print('PASS: DSE ERP 9.0.34 final cosmetic + Quotation first-open bootstrap contract')
