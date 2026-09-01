#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
fail=[]
def text(p): return (ROOT/p).read_text(encoding='utf-8',errors='ignore')
def need(ok,msg):
    if not ok: fail.append(msg)
# version identity
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
need('APP_VERSION = "9.0.52"' in runtime and 'BUILD_REVISION = "9.0.52"' in runtime,'runtime identity is not 9.0.52')
for p in ('pom.xml','desktop/pom.xml','server/pom.xml','shared/pom.xml'):
    need('9.0.52' in text(p),f'{p} not bumped to 9.0.52')
# exactly two themes and final 3D semantic ownership
cssdir=ROOT/'desktop/src/main/resources/css'; css=sorted(p.name for p in cssdir.glob('*.css'))
need(css==['dark-theme.css','light-theme.css'],f'two-theme contract changed: {css}')
for name in css:
    s=text('desktop/src/main/resources/css/'+name)
    need('DSE ERP 9.0.52 — canonical 3D semantic action system' in s,f'{name} missing 9.0.52 action layer')
    need('linear-gradient(to bottom right,#2563eb,#6d28d9)' in s,f'{name} missing blue-violet 3D primary gradient')
    need('erp-button-secondary' in s and 'erp-button-success' in s and 'erp-button-warning' in s and 'erp-button-danger' in s,f'{name} semantic variants incomplete')
    need('.button.user-password-eye' in s and '-fx-background-color: transparent' in s,f'{name} password reveal theme treatment missing')
# shared enhancer owns action icon/text semantics
icons=text('desktop/src/main/java/org/example/util/IconFactory.java')
need('neutral document/action fallback rather than no icon' in icons and 'semantic = originalText.isBlank() ? "actions" : "document"' in icons,'global action icon fallback missing')
need('erp-button-primary' in icons and 'erp-button-secondary' in icons and 'erp-button-danger' in icons,'global semantic button variants missing')
need('if (value.contains("back")) return "previous";' in icons,'Back navigation is not directional')
nav=text('desktop/src/main/java/org/example/navigation/NavigationManager.java')
need('ProfessionalUiEnhancer.enhance(page)' in nav,'normal navigation does not apply shared UI enhancer')
# saved view UI removed exactly where agreed
for f in ('SalesList.fxml','PurchaseList.fxml','Quotations.fxml'):
    s=text('desktop/src/main/resources/fxml/pages/'+f)
    need('text="Save View"' not in s and 'text="Saved Views"' not in s,f'{f} still exposes Saved View controls')
# release notes work both online/offline, including the prior security release
rh=text('desktop/src/main/java/org/example/update/ReleaseHighlights.java'); ud=text('desktop/src/main/java/org/example/update/UpdateDialogs.java')
need('if ("9.0.52".equals(version))' in rh and 'if ("9.0.50".equals(version))' in rh,'packaged release notes missing 9.0.50/9.0.52')
need('public static String resolve(String version, String onlineNotes)' in rh and 'meaningfulLines < 3' in rh,'short/blank online release fallback missing')
need(ud.count('ReleaseHighlights.resolve(')>=2,'What’s New/update dialog does not consistently resolve full notes')
# explicit password eyes use universal transparent treatment and preserve their view/hide icon
udlg=text('desktop/src/main/resources/fxml/pages/UserDialog.fxml'); uc=text('desktop/src/main/java/org/example/controller/UserDialogController.java')
need(udlg.count('password-reveal-button,user-password-eye')==2,'User Dialog eye buttons not on universal reveal style')
need(uc.count('erp.icon.skip')>=2 and 'show ? "hide" : "view"' in uc,'User Dialog eye icon ownership/toggle missing')
# semantic registry includes 9.0.50/51 security captions
reg=text('desktop/src/main/resources/ui/semantic-registry.properties')
for token in ('field.authenticator.verification.code=security','field.authenticator.code.non.admin.only=security','field.registration.role=role','field.assign.final.role=role','kpi.pending.approval=status','header.authenticator=security'):
    need(token in reg,'semantic registry missing '+token)
# critical entry values no longer use the confirmed clipping widths
for f in ('Sale.fxml','Purchase.fxml'):
    s=text('desktop/src/main/resources/fxml/pages/'+f)
    need('maxWidth="88"' not in s and 'maxWidth="90"' not in s,f'{f} retains confirmed narrow Qty/GST caps')
qe=text('desktop/src/main/resources/fxml/pages/QuotationEditor.fxml')
need('fx:id="txtQuantity" text="1.00" promptText="Qty" prefWidth="100"' in qe,'Quotation quantity readability fix missing')
# every FXML remains parseable
count=0
for p in (ROOT/'desktop/src/main/resources/fxml').rglob('*.fxml'):
    try: ET.parse(p); count+=1
    except Exception as e: fail.append(f'FXML parse failed {p.name}: {e}')
need(count==62,f'expected 62 parseable FXML files, got {count}')
if fail:
    print('UI_STABILIZATION_9_0_51_FAIL')
    for x in fail: print(' -',x)
    sys.exit(1)
print('UI_STABILIZATION_9_0_51_OK fxml=62 css=2 saved_view_controls_removed=6 release_notes=offline_safe button_icons=central semantic_labels=rebased')
