from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def require(condition, message):
    if not condition:
        raise SystemExit('IMPORT_9_0_3_CONTRACT_FAIL: ' + message)

import_fxml = text('desktop/src/main/resources/fxml/pages/Import.fxml')
import_controller = text('desktop/src/main/java/org/example/controller/ImportController.java')
layout = text('desktop/src/main/java/org/example/util/SpreadsheetLayoutDetector.java')
pr_service = text('server/src/main/java/org/example/server/recon/PurchaseReconService.java')
pr_repo = text('server/src/main/java/org/example/server/persistence/repository/PurchaseReconRepository.java')
pr_batch_repo = text('server/src/main/java/org/example/server/persistence/repository/PurchaseReconImportBatchRepository.java')
migration = text('server/src/main/resources/db/migration/V9_0_3__import_scalability.sql')
bank_repo = text('server/src/main/java/org/example/server/persistence/repository/BankStatementImportRepository.java')
bank_service = text('server/src/main/java/org/example/server/reconciliation/BankReconciliationService.java')
bank_controller = text('server/src/main/java/org/example/server/reconciliation/BankReconciliationController.java')
bank_client = text('desktop/src/main/java/org/example/api/bank/BankStatementApiClient.java')
bank_fxml = text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
bank_ui = text('desktop/src/main/java/org/example/controller/BankStatementController.java')
runtime = text('shared/src/main/java/org/example/shared/RuntimeContract.java')
server_props = text('server/src/main/resources/application.properties')

# Review and validation are independent UI surfaces.
require('fx:id="tblPreview"' in import_fxml and 'fx:id="tblValidation"' in import_fxml,
        'Import review must keep separate data-preview and validation tables')
require('fx:id="dataPreviewTab"' in import_fxml and 'fx:id="validationResultsTab"' in import_fxml,
        'Import review tabs must expose Data Preview and Validation Results')
show_validation = import_controller[import_controller.index('private void showValidationTable'):]
show_validation = show_validation[:show_validation.index('\n    private ', 20)]
require('tblValidation.getColumns().clear()' in show_validation and 'tblPreview.getColumns().clear()' not in show_validation,
        'Preflight validation must never clear the mapped-data preview')
require('SpreadsheetLayoutDetector.detectAll' in import_controller and '_source_sheet' in import_controller and '_source_row' in import_controller,
        'Purchase Recon preview/import must retain multi-sheet source traceability')
require('public static List<Layout> detectAll' in layout,
        'Spreadsheet layout detector must support all matching visible worksheets')

# Purchase Recon safe re-import policy.
for action in ('ALREADY CURRENT', 'DUPLICATE IN FILE', 'CONFLICT', 'BANK-LINKED', 'SUPPLIER IDENTITY CONFLICT', 'UPDATE'):
    require(action in pr_service, f'Purchase Recon import action missing: {action}')
require('findBusinessKeyForUpdate' in pr_repo and 'findBusinessKeyForUpdate' in pr_service,
        'Purchase Recon updates must lock and resolve the canonical supplier/invoice/FY business key')
require('findBySourceFingerprint' not in pr_batch_repo,
        'Purchase Recon workbook fingerprint must not remain a permanent duplicate blocker')
require('DROP CONSTRAINT IF EXISTS purchase_recon_import_batch_source_fingerprint_key' in migration,
        '9.0.3 migration must remove the unique workbook fingerprint constraint')
require('ADD COLUMN IF NOT EXISTS source_sheet' in migration,
        '9.0.3 migration must persist source worksheet traceability')

# Bank Statement history must be bounded and pageable.
require('Page<BankStatementImportEntity> search' in bank_repo and 'findAllByOrderByImportedAtDesc' not in bank_repo,
        'Bank Statement import history must use server-side paging, not an unbounded repository read')
require('batchPage(0,20' in bank_service and 'PageRequest.of' in bank_service,
        'Bank Statement recent selector must be bounded and full history pageable')
require('@GetMapping("/imports/page")' in bank_controller and '@GetMapping("/imports/{id}")' in bank_controller,
        'Bank Statement API must expose paged history and direct batch lookup')
require('batchPage(0,20' in bank_client and 'BatchPage' in bank_client,
        'Desktop Bank Statement API must load only recent history by default')
require('fx:id="btnBrowseStatements"' in bank_fxml and 'fx:id="statementHistoryDrawer"' in bank_fxml and 'fx:id="tableHistory"' in bank_fxml,
        'Bank Statement screen must provide the statement-history browser')
require('loadStatementHistory' in bank_ui and 'api.batchPage' in bank_ui,
        'Bank Statement history drawer must use the paginated API')
require('findBySourceFingerprint' in bank_service and 'new BankReconciliationDtos.ImportResult(batch(existing),0' in bank_service and ',true)' in bank_service,
        'Exact Bank Statement fingerprints must resolve to the prior batch instead of creating duplicates')
require('txs.existsByTransactionFingerprint' in bank_service,
        'Overlapping Bank Statements must preserve transaction-level duplicate protection')

# Desktop/server startup compatibility contract.
require('APP_VERSION = "9.0.52"' in runtime and 'BUILD_REVISION = "9.0.52"' in runtime,
        'Desktop APP_VERSION and BUILD_REVISION must both be 9.0.18')
require('dse.app.version=9.0.52' in server_props and 'dse.build.revision=9.0.52' in server_props,
        'Spring Boot version/build must match the desktop 9.0.18 runtime contract')

print('IMPORT_9_0_3_CONTRACT_OK')
