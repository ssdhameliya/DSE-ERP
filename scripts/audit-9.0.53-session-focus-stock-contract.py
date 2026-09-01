#!/usr/bin/env python3
from pathlib import Path
import sys,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def t(p): return (R/p).read_text(encoding='utf-8',errors='ignore')
def need(ok,msg):
    if not ok: print('FAIL:',msg);sys.exit(1)
# identity
need('<version>9.0.56</version>' in t('pom.xml') and '<dse.phase>9.0.56</dse.phase>' in t('pom.xml'),'root identity')
need('APP_VERSION = "9.0.56"' in t('shared/src/main/java/org/example/shared/RuntimeContract.java'),'runtime identity')
# session
sm=t('desktop/src/main/java/org/example/service/SessionActivityManager.java')
for token in ['10 * 60','2 * 60','Stay Signed In','Log Out Now','MouseEvent.MOUSE_MOVED','logoutIdle','extendSession','Window.getWindows()']:
    need(token in sm,'session manager missing '+token)
auth=t('server/src/main/java/org/example/server/auth/AuthService.java')
need('SESSION_EXTENDED' in auth and 'AUTO_LOGOUT_IDLE' in auth and 'MANUAL_LOGOUT' in auth,'server session audit')
need('/session/extend' in t('server/src/main/java/org/example/server/auth/AuthController.java'),'session extend endpoint')
# focus and search icon cleanup
focus=t('desktop/src/main/java/org/example/util/WorkflowFocusManager.java')
for token in ['KeyCode.TAB','KeyCode.ENTER','selectAllOnFocus','initial(Node node)']:
    need(token in focus,'focus manager missing '+token)
sales=t('desktop/src/main/java/org/example/controller/SalesController.java')
need('installBusinessFocusOrder' in sales and 'KeyCode.S' in sales,'Sale focus/Ctrl+S')
need('new MenuItem(itemSearchDisplay(item));' in sales,'Sale item search must be text-only')
need('new MenuItem(itemSearchDisplay(item), IconFactory.compactIcon("item", 15))' not in sales,'Sale item result icon still present')
# sales-order stock
ws=t('server/src/main/java/org/example/server/workflow/WorkflowService.java')
for token in ['stockAvailability(String itemCode,Integer documentId)','findByItemCodeForUpdate','adjustReservations','getReservedStock','SALES_ORDER']:
    need(token in ws,'workflow stock authority missing '+token)
need('currentOrderReserved' in t('server/src/main/java/org/example/server/workflow/WorkflowDtos.java'),'stock DTO must carry own-order reservation')
wc=t('desktop/src/main/java/org/example/controller/WorkflowDocumentController.java')
for token in ['Free to promise','Requested','SHORT','confirmStock','Available: ','new MenuItem(text)','UI_WORKFLOW_FORM_CONTRACT']:
    need(token in wc,'Sales Order UI missing '+token)
need('new MenuItem(text,' not in wc,'Sales Order item search result must not have an icon')
need('stock-availability' in t('server/src/main/java/org/example/server/workflow/WorkflowController.java'),'stock endpoint')
# project UI invariants
fxml=list((R/'desktop/src/main/resources/fxml').rglob('*.fxml'))
need(len(fxml)==65,f'expected 65 FXML, got {len(fxml)}')
for f in fxml: ET.parse(f)
need(sorted(x.name for x in (R/'desktop/src/main/resources/css').glob('*.css'))==['dark-theme.css','light-theme.css'],'exactly two runtime CSS files')
for name in ['light-theme.css','dark-theme.css']:
    css=t('desktop/src/main/resources/css/'+name);need('session-timeout-countdown' in css and 'sales-order-stock-availability' in css,name+' missing 9.0.56 rules')
print('SESSION_FOCUS_STOCK_9_0_53_OK timeout=10m warning=2m focus=central stock=reserved/free-to-promise item-icons=text-only')
