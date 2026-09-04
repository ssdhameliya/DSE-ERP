#!/usr/bin/env python3
from pathlib import Path
import sys,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def t(p): return (R/p).read_text(encoding="utf-8")
def need(ok,msg):
    if not ok: print("FAIL:",msg);sys.exit(1)
need("<version>9.0.79</version>" in t("pom.xml") and "<dse.phase>9.0.79</dse.phase>" in t("pom.xml"),"root version")
need('APP_VERSION = "9.0.79"' in t("shared/src/main/java/org/example/shared/RuntimeContract.java"),"runtime version")
fx=t("desktop/src/main/resources/fxml/pages/Customer360.fxml"); ctl=t("desktop/src/main/java/org/example/controller/Customer360Controller.java")
ET.parse(R/"desktop/src/main/resources/fxml/pages/Customer360.fxml")
for label in ("Overview","Contacts","Quotations","Invoices","Payments","Notes","Documents"):
    need(('text="'+label+'"') in fx,"missing Customer 360 tab "+label)
for removed in ("Sales Orders","Projects","Project Execution","Direct / Unlinked Sales"):
    need(removed not in fx,"removed Project Execution concept remains in Customer 360: "+removed)
for action in ("Back to Customers","Edit Customer","New Sale"):
    need(('text="'+action+'"') in fx,"missing header action "+action)
need('CustomerSaleContext.select(customer.getId())' in ctl and '"/fxml/pages/Sale.fxml"' in ctl,"New Sale must reuse existing Sale screen")
server=t("server/src/main/java/org/example/server/customer360/Customer360Service.java")
for authority in ("quotation_header","sales_header","payment_record","party_contact","party_note"):
    need(authority in server,"missing server authority "+authority)
for removed in ("workflow_document","PROJECT_EXECUTION","sales_order_no","project_no"):
    need(removed not in server,"Project Execution dependency remains in Customer 360 service: "+removed)
need("CUSTOMERS.EDIT" in server and "row_version" in server,"multi-user contact/note protection")
need(len(list((R/"desktop/src/main/resources/fxml").rglob("*.fxml")))==59,"expected 59 FXML after Project Execution removal")
need(sorted(p.name for p in (R/"desktop/src/main/resources/css").glob("*.css"))==["dark-theme.css","light-theme.css"],"exactly two themes")
print("CUSTOMER_360_CONTRACT_OK fxml=59 tabs=7 project_execution=removed")
