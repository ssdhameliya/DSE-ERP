from pathlib import Path
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding='utf-8')
def req(ok,msg):
    if not ok: raise SystemExit('FAIL: '+msg)
pr=text('desktop/src/main/java/org/example/controller/PurchaseReturnsController.java')
bs=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
ru=text('desktop/src/main/java/org/example/util/RegisterUiSupport.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
dash=(ROOT/'desktop/src/main/resources/fxml/pages/Dashboard.fxml').read_bytes()
req('APP_VERSION = "9.0.32"' in runtime and 'BUILD_REVISION = "9.0.32"' in runtime,'9.0.32 runtime identity')
req('import org.example.util.ScreenRefreshPolicy;' in pr,'PurchaseReturnsController must import ScreenRefreshPolicy')
req('private void refresh(){reloadCurrentPage();}' in bs,'BankStatementController must retain internal refresh() for post-action callbacks')
req('@FXML private void refreshWithFeedback()' in bs,'Bank Statement Refresh button must keep explicit feedback handler')
req('root.requestLayout();' not in ru,'RegisterUiSupport must not call requestLayout() on Node')
req('if (root instanceof Parent parent)' in ru and 'parent.requestLayout();' in ru,'RegisterUiSupport must request layout through Parent')
req(sha256(dash).hexdigest()=='afe7a8e641dd7e1d3574fd8a5d912228837b0129456bc5bc11b823550d8aec8f','Dashboard Global Search/layout FXML must remain untouched')
print('PASS: DSE ERP 9.0.32 compile integration contract')
