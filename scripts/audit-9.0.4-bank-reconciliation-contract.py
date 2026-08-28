from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
failures=[]
def text(rel):
    p=ROOT/rel
    if not p.exists():
        failures.append(f'Missing required file: {rel}')
        return ''
    return p.read_text(encoding='utf-8', errors='replace')
def require(ok,msg):
    if not ok: failures.append(msg)

service=text('server/src/main/java/org/example/server/reconciliation/BankReconciliationService.java')
dtos=text('server/src/main/java/org/example/server/reconciliation/BankReconciliationDtos.java')
entity=text('server/src/main/java/org/example/server/persistence/entity/BankReconciliationAllocationEntity.java')
controller=text('server/src/main/java/org/example/server/reconciliation/BankReconciliationController.java')
security=text('server/src/main/java/org/example/server/security/SecurityConfig.java')
runner=text('server/src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java')
migration=text('server/src/main/resources/db/migration/V9_0_4__bank_reconciliation_rounding.sql')
return_service=text('server/src/main/java/org/example/server/returns/ReturnService.java')
client=text('desktop/src/main/java/org/example/api/bank/BankStatementApiClient.java')
ui=text('desktop/src/main/java/org/example/controller/BankStatementController.java')
fxml=text('desktop/src/main/resources/fxml/pages/BankStatement.fxml')
settings=text('desktop/src/main/java/org/example/controller/SettingsController.java')
payment_fxml=text('desktop/src/main/resources/fxml/pages/settings/PaymentSettingsPanel.fxml')
config=text('desktop/src/main/java/org/example/config/ConfigManager.java')
runtime=text('shared/src/main/java/org/example/shared/RuntimeContract.java')
props=text('server/src/main/resources/application.properties')

# Exact 50/45/5 suggestion model: amount remains strict and document number contributes no score.
start=service.find('private BankReconciliationDtos.CandidateDto candidate(')
end=service.find('private void refreshBatch', start)
candidate=service[start:end] if start >= 0 and end > start else ''
require('Math.abs(outstanding-amount)<=.01' in candidate and 'score+=50' in candidate,
        'Suggestion amount signal must be exact ±0.01 and worth 50 points')
require('score+=45' in candidate and 'GENERIC_PARTY_TOKENS' in service,
        'Useful party-name signal must be worth 45 points and filter generic business tokens')
require('ChronoUnit.DAYS.between' in candidate and 'score+=5' in candidate,
        '±7-day date signal must be worth 5 points')
require('score+=20' not in candidate and 'documentNo' not in candidate,
        'Document/reference number must not contribute suggestion confidence')
require('confidence()>=75' in service,
        'Suggestion threshold must remain 75')

# Explicit round-off model and shared setting.
require('ADD COLUMN IF NOT EXISTS rounding_adjustment' in migration and migration.count('rounding_adjustment') >= 2,
        '9.0.4 migration must add explicit round-off storage to allocation and return refund records')
require("payment.bankMatchRoundingTolerance','1.00'" in migration,
        '9.0.4 migration must seed the ₹1.00 default Bank Match round-off tolerance')
require('V9_0_4__bank_reconciliation_rounding' in runner,
        '9.0.4 reconciliation migration must be registered in runtime migration runner')
require('getRoundingAdjustment' in service and 'setRoundingAdjustment' in service and 'roundingTolerance()' in service,
        'Matching/reversal must persist and use explicit round-off adjustment')
require('rr.amount+COALESCE(rr.rounding_adjustment,0)' in return_service or 'amount+COALESCE(rounding_adjustment,0)' in return_service,
        'Return refund totals must include reconciliation round-off')
require('txtBankMatchRoundingTolerance' in settings and 'tolerance < 0 || tolerance > 5' in settings,
        'Settings must expose and validate Bank Match round-off tolerance from ₹0 to ₹5')
require('Round-off Tolerance (₹)' in payment_fxml and 'payment.bankMatchRoundingTolerance' in config,
        'Payment settings and ConfigManager must expose the shared round-off policy')
require('roundOffValue' in ui and 'Full Match + Round-off' in ui and 'bankMatchRoundingTolerance' in ui,
        'Match workspace must show round-off separately from the actual bank amount')

# Safe statement deletion: permission + double confirmation + transactional rollback.
require('@DeleteMapping("/imports/{id}")' in controller and 'deleteBatch' in controller,
        'Bank Statement API must expose statement deletion')
require('BANK_EXPENSE.DELETE' in service and '"DELETE".equals' in service and 'reverseInternal' in service,
        'Server deletion must require delete permission, typed DELETE and rollback reconciliation effects')
require('HttpMethod.DELETE, "/api/bank-statements/imports/**"' in security and 'BANK_EXPENSE.DELETE' in security,
        'Bank Statement DELETE route must be protected by the permission matrix')
require('BatchDeleteResult' in dtos and 'BatchDeleteResult' in client and 'deleteBatch(' in client,
        'Desktop/server contracts must expose deletion results')
require('fx:id="btnDeleteStatement"' in fxml and 'fx:id="btnHistoryDelete"' in fxml,
        'Current statement and History drawer must both expose Delete Statement actions')
require('Delete Bank Statement?' in ui and 'Type DELETE to confirm:' in ui and 'PermissionService.allowed("BANK_EXPENSE.DELETE")' in ui,
        'Desktop deletion must use impact confirmation, typed DELETE and permission control')

# Startup compatibility: one exact version/build across desktop and Spring Boot.
require('APP_VERSION = "9.0.32"' in runtime and 'BUILD_REVISION = "9.0.32"' in runtime,
        'Desktop app version and build revision must both be 9.0.18')
require('dse.app.version=9.0.32' in props and 'dse.build.revision=9.0.32' in props,
        'Spring Boot app/build version must exactly match desktop 9.0.18')

if failures:
    print('BANK_RECON_9_0_4_CONTRACT_FAIL')
    for failure in failures: print(' -',failure)
    sys.exit(1)
print('BANK_RECON_9_0_4_CONTRACT_OK')
