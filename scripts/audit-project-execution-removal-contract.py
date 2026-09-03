#!/usr/bin/env python3
from pathlib import Path
import sys
R=Path(__file__).resolve().parents[1]
def text(p): return (R/p).read_text(encoding="utf-8",errors="ignore")
def need(ok,msg):
    if not ok: print("FAIL:",msg);sys.exit(1)
removed=[
"server/src/main/java/org/example/server/workflow/WorkflowController.java",
"server/src/main/java/org/example/server/workflow/WorkflowDtos.java",
"server/src/main/java/org/example/server/workflow/WorkflowService.java",
"desktop/src/main/java/org/example/api/workflow/WorkflowApiClient.java",
"desktop/src/main/java/org/example/controller/WorkflowDocumentController.java",
"desktop/src/main/resources/fxml/pages/Projects.fxml",
"desktop/src/main/resources/fxml/pages/SalesOrders.fxml",
"desktop/src/main/resources/fxml/pages/PurchaseOrders.fxml",
"desktop/src/main/resources/fxml/pages/GoodsReceipts.fxml",
"desktop/src/main/resources/fxml/pages/Dispatches.fxml",
"desktop/src/main/resources/fxml/pages/WorkflowEditor.fxml"]
for p in removed: need(not (R/p).exists(),"removed Project Execution artifact still exists: "+p)
for p in [
"shared/src/main/java/org/example/shared/PermissionCatalog.java",
"server/src/main/java/org/example/server/master/MasterDataService.java",
"desktop/src/main/resources/fxml/pages/Dashboard.fxml",
"desktop/src/main/java/org/example/controller/DashboardController.java",
"server/src/main/java/org/example/server/customer360/Customer360Service.java",
"desktop/src/main/resources/fxml/pages/Customer360.fxml",
"desktop/src/main/java/org/example/controller/SalesController.java",
"desktop/src/main/java/org/example/controller/PurchaseController.java"]:
    s=text(p)
    for token in ["PROJECT_EXECUTION","REF_PROJECT","REF_SALES_ORDER","REF_PURCHASE_ORDER","REF_GRN","REF_DISPATCH","workflow_document","project_no","sales_order_no","dispatch_no","purchase_order_no","grn_no"]:
        need(token not in s,f"{token} remains in {p}")
mig=text("server/src/main/resources/db/migration/V9_0_62__remove_project_execution.sql")
for token in ["DROP TABLE IF EXISTS workflow_document","DROP COLUMN IF EXISTS project_no","DROP COLUMN IF EXISTS sales_order_no","DROP COLUMN IF EXISTS dispatch_no","DROP COLUMN IF EXISTS purchase_order_no","DROP COLUMN IF EXISTS grn_no","PROJECT_EXECUTION"]:
    need(token in mig,"cleanup migration missing "+token)
need("V9_0_62__remove_project_execution" in text("server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java"),"cleanup migration not registered")
print("PROJECT_EXECUTION_REMOVAL_OK runtime=absent schema_cleanup=yes linked_sales_purchase_c360_master_refs=removed")
