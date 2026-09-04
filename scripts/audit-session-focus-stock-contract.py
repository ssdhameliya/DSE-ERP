#!/usr/bin/env python3
from pathlib import Path
import sys,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def t(p): return (R/p).read_text(encoding="utf-8",errors="ignore")
def need(ok,msg):
    if not ok: print("FAIL:",msg);sys.exit(1)
need("<version>9.0.77</version>" in t("pom.xml") and "<dse.phase>9.0.77</dse.phase>" in t("pom.xml"),"root identity")
need('APP_VERSION = "9.0.77"' in t("shared/src/main/java/org/example/shared/RuntimeContract.java"),"runtime identity")
sm=t("desktop/src/main/java/org/example/service/SessionActivityManager.java")
for token in ["10 * 60","2 * 60","Stay Signed In","Log Out Now","MouseEvent.MOUSE_MOVED","logoutIdle","extendSession","Window.getWindows()"]:
    need(token in sm,"session manager missing "+token)
auth=t("server/src/main/java/org/example/server/auth/AuthService.java")
need("SESSION_EXTENDED" in auth and "AUTO_LOGOUT_IDLE" in auth and "MANUAL_LOGOUT" in auth,"server session audit")
need("/session/extend" in t("server/src/main/java/org/example/server/auth/AuthController.java"),"session extend endpoint")
focus=t("desktop/src/main/java/org/example/util/WorkflowFocusManager.java")
for token in ["KeyCode.TAB","KeyCode.ENTER","selectAllOnFocus","initial(Node node)"]:
    need(token in focus,"focus manager missing "+token)
sales=t("desktop/src/main/java/org/example/controller/SalesController.java")
lookup=t("desktop/src/main/java/org/example/document/DocumentLookupPolicy.java")
need("installBusinessFocusOrder" in sales and "KeyCode.S" in sales,"Sale focus/Ctrl+S")
need("new MenuItem(itemSearchSuggestionDisplay(item));" in sales,"Sale item search must remain text-only")
need("DocumentLookupPolicy.itemSuggestionDisplay" in sales,"Sale item search must delegate to shared lookup policy")
for token in ["Category: ","HSN: ","Unit: ","GST: "]:
    need(token in lookup,"Sale item search metadata missing "+token)
fxml=list((R/"desktop/src/main/resources/fxml").rglob("*.fxml"));need(len(fxml)==59,f"expected 59 FXML, got {len(fxml)}")
for f in fxml: ET.parse(f)
need(sorted(x.name for x in (R/"desktop/src/main/resources/css").glob("*.css"))==["dark-theme.css","light-theme.css"],"exactly two runtime CSS files")
for name in ["light-theme.css","dark-theme.css"]:
    need("session-timeout-countdown" in t("desktop/src/main/resources/css/"+name),name+" missing session rules")
print("SESSION_FOCUS_CONTRACT_OK timeout=10m warning=2m focus=central")
