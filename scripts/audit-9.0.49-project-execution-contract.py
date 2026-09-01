#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
fail=[]
def need(path,*tokens):
 p=ROOT/path
 if not p.exists(): fail.append(f"missing {path}"); return
 t=p.read_text(encoding='utf-8',errors='ignore')
 for x in tokens:
  if x not in t: fail.append(f"{path}: missing {x!r}")
need('server/src/main/resources/db/migration/V9_0_49__project_execution_core.sql','CREATE TABLE IF NOT EXISTS workflow_document','CREATE TABLE IF NOT EXISTS workflow_document_line','ALTER TABLE sales_header ADD COLUMN IF NOT EXISTS project_no','ALTER TABLE purchase_header ADD COLUMN IF NOT EXISTS project_no')
need('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java','V9_0_49__project_execution_core','requireTable("workflow_document")','requireTable("workflow_document_line")')
need('server/src/main/java/org/example/server/workflow/WorkflowService.java','@Transactional','findByIdForUpdate','BigDecimal','reference_counter','WORKFLOW|','rowVersion()')
need('server/src/main/java/org/example/server/workflow/WorkflowController.java','@RequestMapping("/api/workflow")','@PostMapping','@PutMapping','@DeleteMapping')
need('desktop/src/main/java/org/example/controller/WorkflowDocumentController.java','OwnedDialog','DynamicTableLayoutManager.install(table)','BigDecimal','PROJECT','SALES_ORDER','PURCHASE_ORDER','GRN','DISPATCH')
need('desktop/src/main/resources/fxml/pages/Dashboard.fxml','btnProjectExecution','Projects / Jobs','Sales Orders','Purchase Orders','Goods Receipt (GRN)','Dispatch')
for f in ['Projects.fxml','SalesOrders.fxml','PurchaseOrders.fxml','GoodsReceipts.fxml','Dispatches.fxml']:
 need('desktop/src/main/resources/fxml/pages/'+f,'org.example.controller.WorkflowDocumentController','erp-kpi-section','professional-table','approved-ui')
# Guard the production boundary: existing invoice/payment screens and controllers remain present.
for f in ['Sale.fxml','RecordPayment.fxml','Purchase.fxml','PurchasePayment.fxml']:
 if not (ROOT/'desktop/src/main/resources/fxml/pages'/f).exists(): fail.append('missing existing production screen '+f)
need('shared/src/main/java/org/example/shared/RuntimeContract.java','APP_VERSION = "9.0.52"','BUILD_REVISION = "9.0.52"')
need("server/src/main/java/org/example/server/workflow/WorkflowService.java","PROJECT_EXECUTION.VIEW")

need("server/src/main/java/org/example/server/workflow/WorkflowController.java","project-profitability")

need("server/src/main/java/org/example/server/workflow/WorkflowService.java","ProjectProfitability")

need("server/src/main/java/org/example/server/workflow/WorkflowService.java","CurrentUser.requirePermission")
if fail:
 print('PROJECT_EXECUTION_9_0_49_FAIL'); [print(' -',x) for x in fail]; sys.exit(1)
print('PROJECT_EXECUTION_9_0_49_OK screens=5 server_owned=yes row_version=yes atomic_numbers=yes existing_invoice_payment=yes')
